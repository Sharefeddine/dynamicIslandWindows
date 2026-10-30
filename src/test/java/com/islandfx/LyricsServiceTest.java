package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LyricsServiceTest {
    private static MediaInfo track(String title, String artist, long duration) {
        return MediaInfo.session(MediaInfo.Playback.PLAYING, title, artist, "private-player.exe", 12, duration);
    }
    @Test void searchEncodesUnicodeAndSendsOnlyTrackMetadata() {
        String uri = LyricsService.searchUri(track("يا ليل & moi", "فنان français", 180)).toString();
        assertTrue(uri.startsWith("https://lrclib.net/api/search?"));
        String decoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        assertTrue(decoded.contains("track_name=يا ليل & moi"));
        assertTrue(decoded.contains("artist_name=فنان français"));
        assertFalse(decoded.contains("private-player"));
        assertFalse(decoded.contains("180"));
        assertFalse(LyricsService.searchUri(track("hello", "", 180)).toString().contains("artist_name"));
    }
    @Test void infersArtistAndTrackFromCommonVideoTitlesButKeepsTheOriginalFallback() {
        MediaInfo original = track("Zoé - Chanson d'été (Official Music Video)", "Example channel", 180);
        List<MediaInfo> variants = LyricsService.searchVariants(original);
        assertEquals(2, variants.size());
        assertEquals("Chanson d'été", variants.get(0).title());
        assertEquals("Zoé", variants.get(0).artist());
        assertEquals(original, variants.get(1));
        assertEquals(1, LyricsService.searchVariants(track("Song - Remastered", "Artist", 180)).size());
    }
    @Test void parsesMultipleTimedLinesFractionsOffsetsAndArabic() {
        List<LyricsService.Line> lines = LyricsService.parseLrc("""
                [ar:example]
                [offset:100]
                [00:12.5][00:20.02]مرحبا بالعالم
                [00:03.001]Bonjour à toi
                [00:61.00]invalid
                [00:04.00]
                """);
        assertEquals(3, lines.size());
        assertEquals(3101, lines.get(0).millis());
        assertEquals("Bonjour à toi", lines.get(0).text());
        assertEquals(12600, lines.get(1).millis());
        assertEquals(20120, lines.get(2).millis());
        assertEquals("مرحبا بالعالم", lines.get(2).text());
        assertEquals(-1, LyricsModel.lineAt(lines, 3100));
        assertEquals(0, LyricsModel.lineAt(lines, 3101));
        assertEquals(2, LyricsModel.lineAt(lines, 30000));
        assertTrue(LyricsModel.isRtl(lines.get(2).text()));
        assertFalse(LyricsModel.isRtl(lines.get(0).text()));
    }
    @Test void matchesTrackAndArtistConservativelyAndPrefersGoodDuration() throws Exception {
        String json = """
          [
            {"trackName":"Chanson d'été", "artistName":"Other", "duration":180, "plainLyrics":"Wrong artist"},
            {"trackName":"Chanson d'été", "artistName":"Zoé", "duration":270, "plainLyrics":"Wrong version"},
            {"trackName":"Chanson d'été", "artistName":"Zoé", "duration":180,
             "plainLyrics":"Bonjour", "syncedLyrics":"[00:02.00]Bonjour à toi\\n[00:04.00]Encore"}
          ]
          """;
        LyricsService.Result result = LyricsService.parseResponse(json, track("Chanson d'été", "Zoé", 180));
        assertEquals(LyricsService.Kind.SYNCED, result.kind());
        assertEquals("Bonjour à toi", result.lines().get(0).text());
        assertEquals("Zoé", result.artist());
        assertEquals(LyricsService.Kind.NOT_FOUND, LyricsService.parseResponse(json, track("Chanson d'été", "Unknown", 180)).kind());
        assertEquals(LyricsService.Kind.NOT_FOUND, LyricsService.parseResponse(json, track("Different", "Zoé", 180)).kind());
    }
    @Test void handlesPlainInstrumentalArabicAndBadPayloads() throws Exception {
        String plain = "[{\"trackName\":\"يا ليل\",\"artistName\":\"فنان\",\"plainLyrics\":\"يا ليل\\nيا عين\"}]";
        LyricsService.Result result = LyricsService.parseResponse(plain, track("يَا لَيْل", "فنان", 180));
        assertEquals(LyricsService.Kind.PLAIN, result.kind());
        assertEquals(List.of("يا ليل", "يا عين"), result.lines().stream().map(LyricsService.Line::text).toList());
        assertEquals(-1, result.lines().get(0).millis());
        result = LyricsService.parseResponse("[{\"trackName\":\"Music\",\"artistName\":\"A\",\"instrumental\":true}]", track("Music", "A", 100));
        assertEquals(LyricsService.Kind.INSTRUMENTAL, result.kind());
        assertThrows(java.io.IOException.class, () -> LyricsService.parseResponse("{}", track("Music", "A", 100)));
        assertTrue(LyricsService.parseLrc("[00:abc]oops").isEmpty());
    }
}
