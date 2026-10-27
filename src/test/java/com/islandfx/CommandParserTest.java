package com.islandfx;

import static com.islandfx.CommandParser.Intent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CommandParserTest {
    @Test void recognizesOnlySupportedLocalCommands() {
        assertEquals(PLAY_PAUSE, CommandParser.parse("play music"));
        assertEquals(PLAY_PAUSE, CommandParser.parse("Pause the song!"));
        assertEquals(NEXT, CommandParser.parse("play next track"));
        assertEquals(PREVIOUS, CommandParser.parse("previous track"));
        assertEquals(TIME, CommandParser.parse("What time is it?"));
        assertEquals(BATTERY, CommandParser.parse("battery charge"));
        assertEquals(HELP, CommandParser.parse("what can you do"));
        assertEquals(STOP_LISTENING, CommandParser.parse("stop listening"));
        assertEquals(UNKNOWN, CommandParser.parse("stop the music"));
        assertEquals(UNKNOWN, CommandParser.parse("playground"));
        assertEquals(UNKNOWN, CommandParser.parse(null));
    }
}
