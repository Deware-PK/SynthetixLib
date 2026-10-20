package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlScriptTest {

    @Test
    void statementsEndAtASemicolonAtTheEndOfALine() {
        assertEquals(List.of("CREATE TABLE a (id INTEGER)", "INSERT INTO a VALUES (1)", "INSERT INTO a\n  VALUES (2)"),
                SqlScript.split("CREATE TABLE a (id INTEGER);\nINSERT INTO a VALUES (1);  \nINSERT INTO a\n  VALUES (2);"));
    }

    @Test
    void aSemicolonInTheMiddleOfALineDoesNotSplit() {
        assertEquals(List.of("SELECT 1; SELECT 2"), SqlScript.split("SELECT 1; SELECT 2;\n"));
    }

    @Test
    void commentsAreDroppedAlsoAfterAStatementAndOnTheirOwnLine() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScript.split("""
                -- start; of file
                SELECT 1; -- after it
                -- between
                SELECT 2 -- before the end
                ;
                -- last
                """));
    }

    @Test
    void quotesProtectSemicolonsAndDashes() {
        assertEquals(List.of("INSERT INTO t VALUES ('a;\nb -- c', \"d;\", `e;`)", "SELECT 'it''s;'"),
                SqlScript.split("INSERT INTO t VALUES ('a;\nb -- c', \"d;\", `e;`);\nSELECT 'it''s;';\n"));
    }

    @Test
    void emptyStatementsAndAScriptWithoutTheLastSemicolon() {
        assertEquals(List.of(), SqlScript.split("\n;\n  ;\n-- nothing\n"));
        assertEquals(List.of("SELECT 1"), SqlScript.split("SELECT 1"));
    }

    @Test
    void bothLineEndingsSplit() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScript.split("SELECT 1;\r\nSELECT 2;\r\n"));
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScript.split("SELECT 1;\rSELECT 2;\r"));
    }

    @Test
    void theChecksumIgnoresLineEndingsAndAByteOrderMarkButNotContent() {
        final String lf = "SELECT 1;\nSELECT 2;\n";
        final String expected = SqlScript.sha256(SqlScript.normalize(lf));
        assertEquals(expected, SqlScript.sha256(SqlScript.normalize("SELECT 1;\r\nSELECT 2;\r\n")));
        assertEquals(expected, SqlScript.sha256(SqlScript.normalize("SELECT 1;\rSELECT 2;\r")));
        assertEquals(expected, SqlScript.sha256(SqlScript.normalize("﻿" + lf)));
        assertNotEquals(expected, SqlScript.sha256(SqlScript.normalize("SELECT 1;\nSELECT 3;\n")));
        assertEquals(64, SqlScript.sha256("x").length());
    }
}
