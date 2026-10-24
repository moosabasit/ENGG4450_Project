package org.jabref.gui.preferences;

import java.util.List;
import java.util.Set;

import org.jabref.gui.entryeditor.EntryEditorTabModel;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JabRefGuiPreferencesTest {

    private static final EntryEditorTabModel PREVIEW =
            new EntryEditorTabModel.BuiltInTab(EntryEditorTabModel.BuiltIn.PREVIEW, true);
    private static final EntryEditorTabModel MAIN =
            new EntryEditorTabModel.BuiltInTab(EntryEditorTabModel.BuiltIn.ALL_FIELDS, true);
    private static final EntryEditorTabModel SOURCE =
            new EntryEditorTabModel.BuiltInTab(EntryEditorTabModel.BuiltIn.SOURCE, true);

        @Test
        @ResourceLock("Preferences")
        void tooltipDurationSurvivesReloadWithFiveSecondDefault() {
                String key = "previewTooltipDuration";
                TestPreferences preferences = new TestPreferences();
                boolean previouslyStored = preferences.hasKey(key);
                String previousValue = preferences.get(key, "");
                try {
                        preferences.removeTooltipDuration();
                        assertEquals(5, preferences.getPreviewPreferences().getEntryPreviewTooltipDuration());
                        preferences.getPreviewPreferences().setEntryPreviewTooltipDuration(30);
                        assertEquals("30", preferences.get(key, ""));
                        assertEquals(30, new JabRefGuiPreferences().getPreviewPreferences().getEntryPreviewTooltipDuration());
                        preferences.getPreviewPreferences().setEntryPreviewTooltipDuration(5);
                        assertEquals(5, new JabRefGuiPreferences().getPreviewPreferences().getEntryPreviewTooltipDuration());
                } finally {
                        // FEATURE: Persistence coverage must restore the contributor's existing duration preference.
                        if (previouslyStored) {
                                preferences.put(key, previousValue);
                        } else {
                                preferences.removeTooltipDuration();
                        }
        }
        }

        @NullMarked
        private static class TestPreferences extends JabRefGuiPreferences {
                void removeTooltipDuration() {
                        remove("previewTooltipDuration");
                }
        }

        @Test
        void storedTabOrderIsApplied() {
        List<EntryEditorTabModel> ordered = JabRefGuiPreferences.applyStoredTabOrder(
                List.of(PREVIEW, MAIN, SOURCE),
                List.of("SOURCE", "ALL_FIELDS"));

        assertEquals(List.of(PREVIEW, SOURCE, MAIN), ordered);
    }

    @Test
    void tabsUnknownToStoredOrderAreAppendedInDefaultOrder() {
        List<EntryEditorTabModel> ordered = JabRefGuiPreferences.applyStoredTabOrder(
                List.of(PREVIEW, MAIN, SOURCE),
                List.of("SOURCE"));

        assertEquals(List.of(PREVIEW, SOURCE, MAIN), ordered);
    }

    @Test
    void duplicateCustomTabNamesAreAllKept() {
        EntryEditorTabModel first = new EntryEditorTabModel.CustomizedFieldsTab("General", List.of("keywords"));
        EntryEditorTabModel second = new EntryEditorTabModel.CustomizedFieldsTab("General", List.of("doi"));

        List<EntryEditorTabModel> ordered = JabRefGuiPreferences.applyStoredTabOrder(
                List.of(PREVIEW, MAIN, first, second),
                List.of("custom:General", "ALL_FIELDS"));

        // The stored-order entry consumes the first duplicate; the second survives at the end.
        assertEquals(List.of(PREVIEW, first, MAIN, second), ordered);
    }

    // [utest->req~entry-editor.custom-tabs.extract-field~1]
    @Test
    void parseCustomTabsReadsPlainStringsAndExtractObjects() {
        assertEquals(
                List.of(new EntryEditorTabModel.CustomizedFieldsTab(
                        "My tab", List.of("author", "doi"), Set.of("doi"))),
                JabRefGuiPreferences.parseCustomTabs(
                        """
                                {"My tab":["author",{"pattern":"doi","extract":true}]}
                                """));
    }
}
