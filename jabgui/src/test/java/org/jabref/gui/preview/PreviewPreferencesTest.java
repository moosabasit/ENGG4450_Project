package org.jabref.gui.preview;

import java.util.List;

import javafx.util.Duration;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.WorkspacePreferences;
import org.jabref.gui.clipboard.ClipBoardManager;
import org.jabref.gui.frame.ExternalApplicationsPreferences;
import org.jabref.gui.maintable.MainTableTooltip;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.preferences.preview.PreviewTabViewModel;
import org.jabref.gui.testutils.JavaFxExtension;
import org.jabref.logic.FilePreferences;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.layout.LayoutFormatterPreferences;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;

import com.airhacks.afterburner.injection.Injector;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NullMarked
@ExtendWith(JavaFxExtension.class)
class PreviewPreferencesTest {
    @BeforeAll
    static void bindServices() {
        Injector.setModelOrService(ClipBoardManager.class, mock(ClipBoardManager.class));
        Injector.setModelOrService(BibEntryTypesManager.class, new BibEntryTypesManager());
    }

    @Test
    void tooltipDurationDefaultsToFiveSeconds() {
        assertEquals(5, PreviewPreferences.getDefault().getEntryPreviewTooltipDuration());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            -2147483648, 1
            0,           1
            1,           1
            30,          30
            600,         600
            2147483647,  600
            """)
    void tooltipDurationIsBounded(int requested, int expected) {
        PreviewPreferences preferences = PreviewPreferences.getDefault();
        preferences.setEntryPreviewTooltipDuration(requested);
        assertEquals(expected, preferences.getEntryPreviewTooltipDuration());
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "601, 600"})
    void importedTooltipDurationIsBounded(int imported, int expected) {
        PreviewPreferences preferences = PreviewPreferences.getDefault();
        preferences.entryPreviewTooltipDurationProperty().set(imported);
        assertEquals(expected, preferences.getEntryPreviewTooltipDuration());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existingTooltipUsesSavedDurationOnNextPreviewAndResetsForPlainText(boolean directPreview) {
        JavaFxExtension.invokeAndWait(() -> {
            PreviewPreferences preferences = PreviewPreferences.getDefault();
            preferences.setShowPreviewEntryTableTooltip(true);
            GuiPreferences guiPreferences = mock(GuiPreferences.class);
            when(guiPreferences.getPreviewPreferences()).thenReturn(preferences);
            when(guiPreferences.getWorkspacePreferences()).thenReturn(WorkspacePreferences.getDefault());
            when(guiPreferences.getFilePreferences()).thenReturn(FilePreferences.getDefault());
            when(guiPreferences.getExternalApplicationsPreferences()).thenReturn(ExternalApplicationsPreferences.getDefault());
            MainTableTooltip tooltip = new MainTableTooltip(mock(DialogService.class), guiPreferences, mock(TaskExecutor.class));
            assertEquals(Duration.seconds(5), tooltip.getShowDuration());
            BibDatabaseContext database = new BibDatabaseContext();
            BibEntry entry = new BibEntry();
            for (int seconds : List.of(30, 60)) {
                preferences.setEntryPreviewTooltipDuration(seconds);
                if (directPreview) {
                    tooltip.createPreviewTooltip(database, entry);
                } else {
                    tooltip.createTooltip(database, entry, "Title");
                }
                assertEquals(Duration.seconds(seconds), tooltip.getShowDuration());
            }
            preferences.setShowPreviewEntryTableTooltip(false);
            tooltip.createTooltip(database, entry, "Title");
            assertEquals(Duration.seconds(5), tooltip.getShowDuration());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "601", "-1", "1.5", "abc", "99999999999999999999"})
    void invalidTooltipDurationPreventsSaving(String value) {
        JavaFxExtension.invokeAndWait(() -> {
            PreviewPreferences preferences = PreviewPreferences.getDefault();
            preferences.setShowPreviewEntryTableTooltip(true);
            PreviewTabViewModel viewModel = createViewModel(preferences);
            viewModel.entryPreviewTooltipDurationProperty().set(value);
            assertFalse(viewModel.validateSettings());
            assertEquals(5, preferences.getEntryPreviewTooltipDuration());
        });
    }

    @Test
    void editingDoesNotChangeTooltipDurationUntilSaved() {
        JavaFxExtension.invokeAndWait(() -> {
            PreviewPreferences preferences = PreviewPreferences.getDefault();
            preferences.setEntryPreviewTooltipDuration(30);
            PreviewTabViewModel viewModel = createViewModel(preferences);
            assertEquals("30", viewModel.entryPreviewTooltipDurationProperty().get());
            viewModel.entryPreviewTooltipDurationProperty().set("60");
            assertEquals(30, preferences.getEntryPreviewTooltipDuration());
            viewModel.setValues();
            assertEquals("30", viewModel.entryPreviewTooltipDurationProperty().get());
            viewModel.entryPreviewTooltipDurationProperty().set("60");
            viewModel.storeSettings();
            assertEquals(60, preferences.getEntryPreviewTooltipDuration());
        });
    }

    @Test
    void disabledPreviewIgnoresInvalidDurationAndRetainsSavedValue() {
        JavaFxExtension.invokeAndWait(() -> {
            PreviewPreferences preferences = PreviewPreferences.getDefault();
            preferences.setEntryPreviewTooltipDuration(30);
            PreviewTabViewModel viewModel = createViewModel(preferences);
            viewModel.entryPreviewTooltipDurationProperty().set("");
            assertFalse(viewModel.tooltipDurationValidationStatus().isValid());
            viewModel.storeSettings();
            assertTrue(viewModel.validateSettings());
            assertEquals(30, preferences.getEntryPreviewTooltipDuration());
        });
    }

    private PreviewTabViewModel createViewModel(PreviewPreferences preferences) {
        PreviewTabViewModel viewModel = new PreviewTabViewModel(mock(DialogService.class), preferences,
                LayoutFormatterPreferences.getDefault(), mock(TaskExecutor.class),
                mock(StateManager.class, Answers.RETURNS_DEEP_STUBS), new JournalAbbreviationRepository());
        viewModel.setValues();
        return viewModel;
    }
}
