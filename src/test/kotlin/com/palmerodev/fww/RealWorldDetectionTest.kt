package com.palmerodev.fww

import com.palmerodev.fww.detection.FlutterWidgetDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RealWorldDetectionTest {

    private val realisticFile = """
        import 'package:flutter/material.dart';

        class ProfileCard extends StatelessWidget {
          const ProfileCard({super.key, required this.name});

          final String name;

          @override
          Widget build(BuildContext context) {
            return Padding(
              padding: const EdgeInsets.only(left: 8, right: 8),
              child: Column(
                children: [
                  Text('Hello, ${'$'}{name}!'),
                  const SizedBox(height: 8),
                  Text("It's a nice day"),
                  Container(
                    decoration: BoxDecoration(color: Color(0xFF00FF00)),
                    child: Text('TARGET'),
                  ),
                ],
              ),
            );
          }
        }
    """.trimIndent()

    @Test
    fun `detects deeply nested target widget in a realistic file`() {
        val cursor = realisticFile.indexOf("'TARGET'")
        val detected = FlutterWidgetDetector.detect("profile_card.dart", realisticFile, cursor)
        assertNotNull("Detection returned null on the TARGET Text in realistic code", detected)
        assertEquals("Text", detected!!.name)
        assertEquals("Container", detected.parentWidgetName)
    }

    @Test
    fun `raw string with backslash does not desync later detection`() {
        val text = """
            Column(
              children: [
                Text(r'C:\Users\path\'),
                Text('AFTER'),
              ],
            )
        """.trimIndent()
        val cursor = text.indexOf("'AFTER'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)
        assertNotNull("Raw string with trailing backslash desynced the scanner", detected)
        assertEquals("Text", detected!!.name)
        assertEquals("Column", detected.parentWidgetName)
    }

    @Test
    fun `nested quotes in interpolation do not desync later detection`() {
        val text = """
            Column(
              children: [
                Text('${'$'}{map['key']}'),
                Text('AFTER'),
              ],
            )
        """.trimIndent()
        val cursor = text.indexOf("'AFTER'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)
        assertNotNull("Nested quotes in interpolation desynced the scanner", detected)
        assertEquals("Text", detected!!.name)
    }

    @Test
    fun `non-widget uppercase call is not offered as a widget`() {
        val text = "Duration(milliseconds: 300)"
        val cursor = 3
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)
        // Ideally Duration should NOT be treated as a wrappable widget.
        assertEquals(
            "Duration is a false positive (treated as a widget)",
            null,
            detected,
        )
    }
}
