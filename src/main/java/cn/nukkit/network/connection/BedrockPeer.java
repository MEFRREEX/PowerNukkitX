package cn.nukkit.network.connection;

import cn.nukkit.compression.NativeByteBufZlib;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.netty.channel.raknet.RakDisconnectReason;
import cn.nukkit.network.connection.netty.BedrockPacketWrapper;
import cn.nukkit.network.connection.netty.codec.FrameIdCodec;
import cn.nukkit.network.connection.netty.codec.batch.BedrockBatchDecoder;
import cn.nukkit.network.connection.netty.codec.compression.CompressionCodec;
import cn.nukkit.network.connection.netty.codec.compression.SnappyCompressionCodec;
import cn.nukkit.network.connection.netty.codec.compression.ZlibCompressionCodec;
import cn.nukkit.network.connection.netty.codec.encryption.BedrockEncryptionDecoder;
import cn.nukkit.network.connection.netty.codec.encryption.BedrockEncryptionEncoder;
import cn.nukkit.network.connection.util.EncryptionUtils;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;

import javax.crypto.SecretKey;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Bedrock peer that represents a single network connection to the remote peer.
 * It can hold one or more {@link BedrockSession}s.
 */
public class BedrockPeer extends ChannelInboundHandlerAdapter {

    public static final String NAME = "bedrock-peer";

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BedrockPeer.class);

    protected final Int2ObjectMap<BedrockSession> sessions = new Int2ObjectOpenHashMap<>();
    protected final Queue<BedrockPacketWrapper> packetQueue = PlatformDependent.newMpscQueue();
    protected final Channel channel;
    protected final BedrockSessionFactory sessionFactory;
    protected ScheduledFuture<?> tickFuture;
    protected AtomicBoolean closed = new AtomicBoolean();

    public BedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        this.channel = channel;
        this.sessionFactory = sessionFactory;
    }

    protected void onBedrockPacket(BedrockPacketWrapper wrapper) {
        int targetId = wrapper.getTargetSubClientId();
        BedrockSession session = this.sessions.computeIfAbsent(targetId, this::onSessionCreated);
        session.onPacket(wrapper);
    }

    protected BedrockSession onSessionCreated(int sessionId) {
        return this.sessionFactory.createSession(this, sessionId);
    }

    protected void checkForClosed() {
        if (this.closed.get()) {
            throw new IllegalStateException("Peer has been closed");
        }
    }

    protected void removeSession(BedrockSession session) {
        this.sessions.remove(session.subClientId, session);
    }

    protected void onTick() {
        if (this.closed.get()) {
            return;
        }

        if (!this.packetQueue.isEmpty()) {
            BedrockPacketWrapper packet;
            while ((packet = this.packetQueue.poll()) != null) {
                this.channel.write(packet);
            }
            this.channel.flush();
        }
    }

    private void onRakNetDisconnect(ChannelHandlerContext ctx, RakDisconnectReason reason) {
        String disconnectReason = BedrockDisconnectReasons.getReason(reason);
        for (BedrockSession session : this.sessions.values()) {
            session.disconnectReason = disconnectReason;
        }
    }

    private void free() {
        for (BedrockPacketWrapper wrapper : this.packetQueue) {
            ReferenceCountUtil.safeRelease(wrapper);
        }
    }

    public void sendPacket(int senderClientId, int targetClientId, DataPacket packet) {
        this.packetQueue.add(new BedrockPacketWrapper(0, senderClientId, targetClientId, packet, null));
    }

    public void sendPacketImmediately(int senderClientId, int targetClientId, DataPacket packet) {
        this.channel.writeAndFlush(new BedrockPacketWrapper(0, senderClientId, targetClientId, packet, null));
    }

    public void enableEncryption(@NonNull SecretKey secretKey) {
        Objects.requireNonNull(secretKey, "secretKey");
        if (!secretKey.getAlgorithm().equals("AES")) {
            throw new IllegalArgumentException("Invalid key algorithm");
        }
        // Check if the codecs exist in the pipeline
        if (this.channel.pipeline().get(BedrockEncryptionEncoder.class) != null ||
                this.channel.pipeline().get(BedrockEncryptionDecoder.class) != null) {
            throw new IllegalStateException("Encryption is already enabled");
        }

        int protocolVersion = ProtocolInfo.CURRENT_PROTOCOL;
        boolean useCtr = protocolVersion >= 428;

        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionEncoder.NAME,
                new BedrockEncryptionEncoder(secretKey, EncryptionUtils.createCipher(useCtr, true, secretKey)));
        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionDecoder.NAME,
                new BedrockEncryptionDecoder(secretKey, EncryptionUtils.createCipher(useCtr, false, secretKey)));

        log.debug("Encryption enabled for {}", getSocketAddress());
    }

    public void setCompression(PacketCompressionAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (handler != null) {
            throw new IllegalArgumentException("Compression is already set");
        }
        ChannelHandler compressionHandler = switch (algorithm) {
            case ZLIB ->  new ZlibCompressionCodec(NativeByteBufZlib.RAW);
            case SNAPPY -> new SnappyCompressionCodec();
        };
        this.channel.pipeline().addBefore(BedrockBatchDecoder.NAME, CompressionCodec.NAME, compressionHandler);
    }

    public void setCompressionLevel(int level) {
        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (handler == null) {
            throw new IllegalArgumentException("Peer has no compression!");
        }
        ((CompressionCodec) handler).setLevel(level);
    }

    public PacketCompressionAlgorithm getCompression() {
        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (!(handler instanceof CompressionCodec)) {
            return null;
        }
        return ((CompressionCodec) handler).getAlgorithm();
    }

    public void close(String reason) {
        for (BedrockSession session : this.sessions.values()) {
            session.disconnectReason = reason;
        }
        this.channel.disconnect();
    }

    protected void onClose() {
        if (this.channel.isOpen()) {
            log.warn("Tried to close peer, but channel is open!", new Throwable());
            return;
        }

        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        if (this.tickFuture != null) {
            this.tickFuture.cancel(false);
            this.tickFuture = null;
        }

        for (BedrockSession session : this.sessions.values())
            try {
                session.onClose();
            } catch (Exception e) {
                log.error("Exception whilst closing session", e);
            }

        this.free();
    }

    public boolean isConnected() {
        return !this.closed.get() && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return !this.channel.isActive() && !this.closed.get();
    }

    public SocketAddress getSocketAddress() {
        return this.channel.remoteAddress();
    }

    public Channel getChannel() {
        return this.channel;
    }

    /*
        Netty handler methods
     */

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        this.onClose();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.sessions.put(0, this.sessionFactory.createSession(this, 0));
        this.tickFuture = this.channel.eventLoop().scheduleAtFixedRate(this::onTick, 50, 50, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.onClose();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof BedrockPacketWrapper) {
                this.onBedrockPacket((BedrockPacketWrapper) msg);
            } else {
                throw new DecoderException("Unexpected message type: " + msg.getClass().getName());
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof RakDisconnectReason) {
            onRakNetDisconnect(ctx, (RakDisconnectReason) evt);
        }
    }

    public long getPing() {
        RakSessionCodec rakSessionCodec = this.channel.pipeline().get(RakSessionCodec.class);
        return rakSessionCodec.getPing();
    }
}
