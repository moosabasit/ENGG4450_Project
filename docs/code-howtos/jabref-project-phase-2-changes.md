> [!IMPORTANT]
> This project does not accept fully AI-generated pull requests. AI tools may only be used for assistance. You must understand and take responsibility for every change you submit.
>
> Read and follow:
> • [AGENTS.md](../../AGENTS.md)
> • [CONTRIBUTING.md](../../CONTRIBUTING.md)

# JabRef Project Phase 2 Changes

This document records both Phase 2 changes: live icon resizing after font preference changes and configurable entry preview tooltip duration.
It consolidates their reproduction steps, implementation details, changed files, verification results, and limitations.
No GitHub issue, comment, assignment, or pull request is modified by this work.

## Live icon resizing after font preference changes

### Issue and scope

[Issue 17556](https://github.com/JabRef/jabref/issues/17556) reports that some icons stay large after changing the font size from 20 to 8 without restarting JabRef.
The original report concerns Linux; the contributor also reproduced the behavior on Windows before this change.
Affected areas include entry-table column headers, the Groups toolbar, and the Groups search icon.
This change implements immediate resizing rather than requiring a restart warning.

### Reproduction

1. Open a library and display the Groups panel and entry table.
2. Open **File > Preferences > General**.
3. Enable **Override default font size**, choose **20**, and save.
4. Open the same preferences, choose **8**, and save without restarting.
5. Compare the Groups controls and entry-table header icons with the surrounding text.

Before the fix, some existing icons retain a previous size while the text changes.
After the fix, adaptive icons follow the current font immediately.
Repeat in both directions and restore the original preferences afterward.

### Confirmed root cause

`ThemeManager` already observes font preferences and updates a font-size style class on each window's scene root.
The preference notification itself is not missing.

Ikonli's `FontIcon` synchronizes its font and integer icon size.
When the size changes, its listener also writes an inline `-fx-font-size` in pixels.
That inline value takes priority over ordinary stylesheet declarations and inherited font sizes.
The icon therefore retains a computed size from an earlier CSS pass.
A plain `-fx-font-size: 1em` stylesheet rule does not override the inline value.

The regression test reproduced the failure before the fix: a label changed from 8pt to 20pt, but its existing font icon remained 10 pixels instead of 26 pixels.

### Implementation

The fix distinguishes adaptive icons from explicitly sized icons.

- `IkonliIcon` marks default-size nodes with the `inherited-icon-size` style class.
  Calling `withSize(...)` creates an explicitly sized icon instead; applying a color preserves that choice.
- `SvgIcon` uses the same distinction so font and SVG backends follow one sizing policy.
- `JabRefIconView` leaves default FXML icons adaptive.
  A constructor size or a change to `glyphSize` explicitly fixes the size.
- The base stylesheet gives adaptive icons `-fx-font-size: 1em !important`.
  The priority is necessary to override Ikonli's generated inline pixel size.
- Context rules preserve the larger main-toolbar, search, Groups, error-console, and walkthrough icons and the normal side-panel header proportions.
  Those relative rules also have the priority needed to remain effective on later CSS passes.
- Fixed CSS sizes in merge controls and walkthrough information remain 14, 18, and 24 pixels respectively.

The fix does not replace icons when preferences change, alter icon colors, add preference listeners to individual icons, or introduce dependencies.
It does not use relative `-fx-icon-size` rules, which can cause feedback between font size and icon size.
Comments beside the changed behavior explain the inline-style workaround and the fixed-size exception.
At the contributor's request, all six code comment blocks introduced by this fix begin with `BUG FIX:`; existing comments are unchanged.

### Changed files

| File | Purpose |
| --- | --- |
| `jabgui/src/main/java/org/jabref/gui/icon/IkonliIcon.java` | Track explicit font-icon sizes and mark adaptive nodes. |
| `jabgui/src/main/java/org/jabref/gui/icon/SvgIcon.java` | Apply the same policy to SVG icons. |
| `jabgui/src/main/java/org/jabref/gui/icon/JabRefIconView.java` | Keep default FXML views adaptive. |
| `jabgui/src/main/resources/org/jabref/gui/theme/internal/jabref-base.css` | Override cached sizes while retaining contextual proportions. |
| `jabgui/src/test/java/org/jabref/gui/theme/ThemeManagerTest.java` | Cover resizing existing nodes and retaining fixed sizes and colors. |
| `docs/requirements/ux.md` | Record the expected user-facing behavior. |
| `CHANGELOG.md` | Describe the user-visible fix and reference the issue. |
| `CHECKLIST.md` | Record the completed local quality gates; PR-only actions do not apply. |
| `docs/code-howtos/jabref-project-phase-2-changes.md` | Consolidate reproduction, diagnosis, changes, verification, and limitations for both changes. |

### Automated verification

Run from the repository root in PowerShell:

```powershell
.\gradlew.bat :jabgui:test --tests '*ThemeManagerTest' --console=plain
```

The parameterized regression has nine cases covering table-header, side-panel header, main-toolbar, search-field, Groups toolbar, and walkthrough-close contexts.
It keeps the same icon nodes while applying **8 > 20 > 8**, then disables the font override.
It checks plain and colored font icons, default FXML views, SVG icons, explicitly sized nodes, and retained colors.
Three additional cases confirm that the fixed CSS sizes remain unchanged for both font and SVG icons.
Expected sizes account for JavaFX's tenth-pixel CSS conversion and Ikonli's integer pixel size.
Stages are closed even when an assertion fails, and tests use isolated workspace preferences rather than changing the user's saved preferences.

The initial two-case regression failed before the fix and passed after the adaptive CSS rule was applied.
The expanded regression cases and neighboring theme/icon tests passed after the final changes.

#### Quality-check results

The following checks completed successfully on Windows with JDK 25:

| Check | Result |
| --- | --- |
| `:jabgui:test` filtered to `ThemeManagerTest`, `ThemeTokenContractTest`, and `IconsPropertiesTest` | Passed, including nine adaptive and three fixed-CSS cases. |
| `:jablib:check` | Passed. No core-library source files changed. |
| `checkstyleMain checkstyleTest checkstyleJmh` | Passed across modules; the touched test was checked again after its final change. |
| `modernizer` | Passed across modules; the GUI module was checked again after its final change. |
| `--no-configuration-cache :rewriteDryRun` | Passed; applying recipes would make no changes. |
| `javadoc` | Passed with documentation warnings. |
| `npx markdownlint-cli2 "docs/**/*.md" "*.md"` | Passed. |
| `npm ci` and `npm run textlint` | Passed; no spelling errors reported. |

OpenRewrite reports missing configured recipes and a parsing warning in the unchanged `SpecialFieldAction.java`.
Its successful result does not mean those unavailable recipes were checked.
The locked npm installation reports four dependency vulnerabilities (one moderate and three high); no dependency upgrades were made as part of this fix.
No full all-module test run, external-service test run, or database test run is claimed.
The local checklist was reviewed point by point; PR actions are not applicable because no PR is being created.

### Manual verification and limitations

Close the old JabRef instance, then launch the changed code:

```powershell
.\gradlew.bat :jabgui:run
```

This initial relaunch loads the modified code; subsequent preference changes should not require a restart.
Repeat the reproduction steps and check that icons shrink immediately, remain legible, and do not overlap nearby text.
Repeat with 8 to 20, then disable the override.
Check the Groups toolbar, its search icon, table headers, and a colored read-status icon.
Open a group icon chooser to check that explicitly sized previews remain consistent.
Capture screenshots at 20 and 8 for human review.

Automated scene tests are run on Windows; Linux-specific rendering and a full interactive visual review still require manual confirmation.

## Configurable entry preview tooltip duration

### Request and baseline

[Issue 17502](https://github.com/JabRef/jabref/issues/17502) requests more time to read the formatted reference and long abstracts shown when hovering over an entry.
A maintainer requested a duration setting rather than only an indefinite timeout.
The contributor reproduced the existing behavior on Windows: the tooltip disappears after roughly five seconds while the pointer remains over the entry.
The original report concerns Kubuntu Linux with KDE Plasma and JabRef 5.15.

### Behavior

Preferences > Entry preview now contains **Entry preview tooltip duration (seconds)** next to the preview-tooltip checkbox.
The numeric input accepts whole numbers from **1 to 600**, with a default of **5**.
It uses the existing preference form's validated input, error display, and disabled-state behavior.
It is disabled when **Show preview in entry table tooltip** is unchecked, without discarding the saved duration.
Empty, fractional, negative, nonnumeric, and out-of-range values prevent saving when preview tooltips are enabled.

Saving changes the duration used the next time a formatted preview is prepared; no application restart is required.
Existing tooltips already displayed are not restarted when a preference is saved.
The setting does not change the hover delay, hide delay, preview content, or entry editor preview panel.
It does not introduce an indefinite display option.
Moving away still uses JavaFX's normal tooltip dismissal behavior.

The shared formatted-preview path used by the Citations tab also honors the duration.
Plain field-value tooltips revert to the original JavaFX timeout rather than retaining a previously applied preview duration.
Other tooltips are unchanged.

### Tooltip implementation

`MainTableTooltip` previously left `showDuration` at JavaFX's default.
Its formatted-preview method now sets `showDuration` from the current saved preference on every preparation.
The ordinary main-table method delegates to that same path for formatted previews and restores its original duration for plain field text.

`PreviewPreferences` owns an observable integer measured in seconds.
Its existing constructors remain compatible, and getters and setters bound values to 1 through 600.
This also protects the runtime from out-of-range imported values.
`JabRefGuiPreferences` loads the `previewTooltipDuration` integer key and registers it with `bindInt`, the existing persistence, import, and reset mechanism.
Installations without the key retain the five-second default.

`PreviewTabViewModel` holds a separate editable string and validates it with a compiled pattern before converting it to an integer.
Typing in the dialog does not change the active preference; only `storeSettings` writes a valid value.
`PreviewTab` adds the localized input through the existing form builder, which also registers its label for preference searches.
All five explanatory comments added for this feature use the contributor's requested `FEATURE:` prefix.
No dependencies, custom timers, global tooltip changes, or new background tasks are introduced.

### Tooltip file changes

| File | Purpose |
| --- | --- |
| `jabgui/src/main/java/org/jabref/gui/preview/PreviewPreferences.java` | Duration property, default, and bounds. |
| `jabgui/src/main/java/org/jabref/gui/preferences/JabRefGuiPreferences.java` | Backing-store load and registered binding. |
| `jabgui/src/main/java/org/jabref/gui/preferences/preview/PreviewTabViewModel.java` | Working copy, validation, and save behavior. |
| `jabgui/src/main/java/org/jabref/gui/preferences/preview/PreviewTab.java` | Searchable, localized preference input. |
| `jabgui/src/main/java/org/jabref/gui/maintable/MainTableTooltip.java` | Formatted-preview timeout and plain-text fallback. |
| `jablib/src/main/resources/l10n/JabRef_en.properties` | English labels and validation text; translations are unchanged. |
| `jabgui/src/test/java/org/jabref/gui/preview/PreviewPreferencesTest.java` | Defaults, bounds, runtime behavior, validation, and unsaved edits. |
| `jabgui/src/test/java/org/jabref/gui/preferences/JabRefGuiPreferencesTest.java` | Backing-store round trip and default-value persistence. |
| `docs/requirements/ux.md` | User-visible duration requirement. |
| `CHANGELOG.md` | End-user feature entry linked to the supplied issue. |
| `CHECKLIST.md` | Local quality-gate status, including newly applicable checks. |
| `docs/code-howtos/jabref-project-phase-2-changes.md` | This consolidated implementation and verification record. |

### Tooltip automated verification

Run the focused tests from the repository root:

```powershell
.\gradlew.bat :jabgui:test --tests '*PreviewPreferencesTest' --tests '*JabRefGuiPreferencesTest' --tests '*PreferencesSearchCoverageTest' --console=plain
```

The 20 focused preference tests passed, covering the default, boundaries, imported values, both preview paths, 30- and 60-second changes, plain-text fallback, invalid input, disabled previews, and edits that are discarded or saved.
Tests inspect the actual JavaFX duration instead of waiting for timers.
The neighboring preference-search tests also passed.
The backing-store test passed separately; it locks preferences and restores the contributor's previous duration key in a `finally` block.
The remaining feature tests use isolated preferences and mocked background execution, without external services.

Final repository quality-gate results:

- `:jablib:check` passed.
- `checkstyleMain checkstyleTest checkstyleJmh modernizer` passed.
- `--no-configuration-cache :rewriteDryRun` reported no changes; it also reported a parse warning for the unchanged `SpecialFieldAction.java`.
- `javadoc` passed with existing documentation warnings.
- `:jablib:test --tests '*LocalizationConsistencyTest*' --rerun-tasks` passed, forcing a fresh scan of source localization keys.
- `npx --yes markdownlint-cli2 "docs/**/*.md" "*.md"` passed for all 181 Markdown files present before documentation consolidation.
- `npm ci` and `npm run textlint` passed. npm reported four dependency vulnerabilities (one moderate and three high); dependency upgrades are outside this change.

These checks do not constitute an all-module test run, database tests, or live external-service tests.

### Manual verification

1. Close the old JabRef instance and launch the changed build with `.\gradlew.bat :jabgui:run`.
2. Open a library containing an entry with a long abstract.
3. Open Preferences > Entry preview, enable **Show preview in entry table tooltip**, set the duration to **30**, and save.
4. Close the entry editor and hover over that entry's title in the main table; keep the mouse still. The preview should remain longer than five seconds and time out at approximately 30 seconds.
5. Move away and hover again; the tooltip should dismiss and reopen normally.
6. Set the duration to **1**, save, and hover again without restarting. The next preview should time out after approximately one second.
7. Enter **0**, **601**, a fractional value, and nonnumeric text. Saving should be blocked while preview tooltips are enabled.
8. Cancel an edit and reopen preferences; the previously saved duration should remain.
9. Disable preview tooltips; the duration input should be disabled. Plain field tooltips should retain their normal timeout.
10. Re-enable previews, save **30**, restart JabRef, and verify that **30** is still selected.
11. Check the Citations tab's formatted preview and capture screenshots of the new preference for human review.

The initial relaunch loads the modified code; subsequent preference edits should not require a restart.
Windows and Linux interactive timing, screenshots, and full preference import/export/reset still require manual confirmation.

## Human review

The contributor must review and understand all changes before submitting them, consistent with the repository's AI usage policy.
For codebase orientation, see [JabRef on DeepWiki](https://deepwiki.com/JabRef/jabref).

<!-- markdownlint-disable-file MD041 -->
