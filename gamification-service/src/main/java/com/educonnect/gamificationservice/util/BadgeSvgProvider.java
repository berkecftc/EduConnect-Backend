package com.educonnect.gamificationservice.util;

import com.educonnect.gamificationservice.model.BadgeType;

import java.util.Map;

public final class BadgeSvgProvider {

    private static final Map<BadgeType, String> SVG_MAP = Map.ofEntries(
            Map.entry(BadgeType.FIRST_STEP, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#8D5524"/>
                      <circle cx="60" cy="60" r="50" fill="#CD853F"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(255,235,200,0.5)" stroke-width="2"/>
                      <polygon points="60,32 66.5,50.5 82,51 70,62 74,78 60,69 46,78 50,62 38,51 53.5,50.5"
                               fill="#FFF8DC" stroke="#D4A017" stroke-width="1"/>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="9" font-weight="bold" fill="#5D4037" letter-spacing="0.5">İLK ADIM</text>
                    </svg>
                    """),
            Map.entry(BadgeType.PROFILE_COMPLETE, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#00695C"/>
                      <circle cx="60" cy="60" r="50" fill="#00897B"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(200,255,240,0.4)" stroke-width="2"/>
                      <circle cx="60" cy="48" r="14" fill="#E0F2F1" stroke="#B2DFDB" stroke-width="1.5"/>
                      <rect x="34" y="67" width="52" height="6" rx="3" fill="#E0F2F1"/>
                      <rect x="38" y="77" width="44" height="5" rx="2.5" fill="rgba(224,242,241,0.6)"/>
                      <polyline points="51,48 57,55 69,42" fill="none" stroke="#00695C" stroke-width="3"
                                stroke-linecap="round" stroke-linejoin="round"/>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="8" font-weight="bold" fill="#004D40" letter-spacing="0.5">PROFİL USTASI</text>
                    </svg>
                    """),
            Map.entry(BadgeType.POINTS_EXPLORER, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#455A64"/>
                      <circle cx="60" cy="60" r="50" fill="#607D8B"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(220,240,255,0.4)" stroke-width="2"/>
                      <text x="60" y="56" text-anchor="middle" font-family="Arial Black,sans-serif"
                            font-size="20" font-weight="900" fill="#ECEFF1">250</text>
                      <text x="60" y="73" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="11" fill="#B0BEC5">PUAN</text>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="9" font-weight="bold" fill="#455A64" letter-spacing="0.5">PUAN KAŞİFİ</text>
                    </svg>
                    """),
            Map.entry(BadgeType.POINTS_MASTER, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#E65100"/>
                      <circle cx="60" cy="60" r="50" fill="#FFB300"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(255,255,200,0.5)" stroke-width="2"/>
                      <text x="60" y="52" text-anchor="middle" font-family="Arial Black,sans-serif"
                            font-size="18" font-weight="900" fill="#BF360C">1000</text>
                      <text x="60" y="70" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="11" fill="#E65100">PUAN</text>
                      <polygon points="47,76 53,72 60,74 67,72 73,76 70,80 60,82 50,80"
                               fill="#BF360C"/>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="9" font-weight="bold" fill="#E65100" letter-spacing="0.5">PUAN USTASI</text>
                    </svg>
                    """),
            Map.entry(BadgeType.WEEK_WARRIOR, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#1B5E20"/>
                      <circle cx="60" cy="60" r="50" fill="#388E3C"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(200,255,200,0.4)" stroke-width="2"/>
                      <text x="60" y="42" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="10" fill="#DCEDC8">GÜN SERİSİ</text>
                      <text x="60" y="78" text-anchor="middle" font-family="Arial Black,sans-serif"
                            font-size="48" font-weight="900" fill="#F1F8E9">7</text>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="8" font-weight="bold" fill="#2E7D32" letter-spacing="0.5">HAFTA SAVAŞÇISI</text>
                    </svg>
                    """),
            Map.entry(BadgeType.FORTNIGHT_WARRIOR, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#0D47A1"/>
                      <circle cx="60" cy="60" r="50" fill="#1976D2"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(200,220,255,0.4)" stroke-width="2"/>
                      <text x="60" y="42" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="10" fill="#BBDEFB">GÜN SERİSİ</text>
                      <text x="60" y="78" text-anchor="middle" font-family="Arial Black,sans-serif"
                            font-size="38" font-weight="900" fill="#E3F2FD">14</text>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="8" font-weight="bold" fill="#1565C0" letter-spacing="0.5">İKİ HAFTA EFSANESİ</text>
                    </svg>
                    """),
            Map.entry(BadgeType.STREAK_LEGEND, """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                      <circle cx="60" cy="60" r="55" fill="#4A148C"/>
                      <circle cx="60" cy="60" r="50" fill="#7B1FA2"/>
                      <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(220,190,255,0.4)" stroke-width="2"/>
                      <text x="60" y="42" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="10" fill="#E1BEE7">GÜN SERİSİ</text>
                      <text x="60" y="78" text-anchor="middle" font-family="Arial Black,sans-serif"
                            font-size="38" font-weight="900" fill="#F3E5F5">28</text>
                      <text x="60" y="121" text-anchor="middle" font-family="Arial,sans-serif"
                            font-size="9" font-weight="bold" fill="#6A1B9A" letter-spacing="0.5">SERİ EFSANESİ</text>
                    </svg>
                    """)
    );

    private BadgeSvgProvider() {
    }

    public static String getSvg(BadgeType badgeType) {
        return SVG_MAP.getOrDefault(badgeType, buildFallbackSvg(badgeType));
    }

    private static String buildFallbackSvg(BadgeType badgeType) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 130">
                  <circle cx="60" cy="60" r="55" fill="#616161"/>
                  <circle cx="60" cy="60" r="50" fill="#9E9E9E"/>
                  <text x="60" y="65" text-anchor="middle" font-family="Arial,sans-serif"
                        font-size="11" fill="white">%s</text>
                </svg>
                """.formatted(badgeType.name());
    }
}
