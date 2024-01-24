package cn.nukkit.lang;


public enum LangCode {
    en_US("English (United States)"),
    en_GB("English (United Kingdom)"),
    de_DE("Deutsch (Deutschland)"),
    es_ES("Español (España)"),
    es_MX("Español (México)"),
    fr_FR("Français (France)"),
    fr_CA("Français (Canada)"),
    it_IT("Italiano (Italia)"),
    ja_JP("日本語 (日本)"),
    ko_KR("한국어 (대한민국)"),
    pt_BR("Português (Brasil)"),
    pt_PT("Português (Portugal)"),
    ru_RU("Русский (Россия)"),
    zh_CN("中文(简体)"),
    zh_TW("中文(繁體)"),
    nl_NL("Nederlands (Nederland)"),
    bg_BG("Български (България)"),
    cs_CZ("Čeština (Česko)"),
    da_DK("Dansk (Danmark)"),
    el_GR("Ελληνικά (Ελλάδα)"),
    fi_FI("Suomi (Suomi)"),
    hu_HU("Magyar (Magyarország)"),
    id_ID("Indonesia (Indonesia)"),
    nb_NO("Norsk bokmål (Norge)"),
    pl_PL("Polski (Polska)"),
    sk_SK("Slovenčina (Slovensko)"),
    sv_SE("Svenska (Sverige)"),
    tr_TR("Türkçe (Türkiye)"),
    uk_UA("Українська (Україна)");

    private final String string;

    LangCode(String string) {
        this.string = string;
    }

    @Override
    public String toString() {
        return this.string;
    }
}
