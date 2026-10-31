# Bug Findings

## Low

### [a/repeated-and-interrupted-operations/extraction-rollback/created-obb-folder-never-removed] A failed extraction leaves behind the SD-card `Android/obb` folder it created

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:720`
  - Related: `FileRepository.kt:625-636` (a protected folder returns before its own removal), `FileRepository.kt:1653` (`claimCoveringDirectory`), `FileRepository.kt:1794-1798` (rollback), `data/util/SystemFolders.kt:38-52` (path-only rule), `util/UncompressHandler.kt:103-109` (MediaStore notification for rolled-back trees)
- **Severity:** Low
- **Confidence:** Low
- **Likelihood:** Low. It needs four things at once: Android 11+, an SD card with no `Android/obb` folder yet, an archive whose entries start with `Android/obb/` extracted into the card's root, and an extraction that then fails or is cancelled. Game OBB archives have exactly that layout, but users normally extract them to internal storage, where `Android/obb` always exists.
- **Defect:** The extraction rollback removes what the extraction created through the same walk the user's delete uses. That walk now never tries to remove a folder whose path is a volume's `Android/data` or `Android/obb`, and the rule checks the path alone. It does not ask whether the folder exists or who created it. When this extraction itself created `<card>/Android/obb`, the rollback empties it but leaves the folder. It also reports the tree as not rolled back, so it is never handed to the MediaStore cleanup. Before this commit the rollback removed the folder and reported it.
- **Trigger:**
  1. API 30+ device with a removable SD card at `/storage/<id>`. The card has `Android/`, which the app's own `getExternalFilesDirs` call creates, but no `Android/obb`. No app has asked for an OBB folder on that card, and this app never does.
  2. With All Files Access on, the user extracts an archive with `Android/obb/<package>/...` entries into the card's root.
  3. The extraction fails or is cancelled. For example, "install unknown apps" is off, so creating `Android/obb/<package>` is refused after `Android/obb` itself was created. A full card or a Cancel does the same.
- **Evidence / verification:**
  - **Traced path:**
    - `claimCoveringDirectory` claims `Android/obb` as the shallowest folder the extraction created.
    - The `catch` block calls `removedTree(<card>/Android/obb)`.
    - `deleteRecursive` sees that the folder matches the protected rule, walks and removes its contents, and returns `systemFolders = 1` without calling `removeFile` on it.
    - `removedTree` now requires `systemFolders == 0`, so it returns false and the path stays out of `rolledBack`.
  - **Baseline:** at `253c20e5`, `deleteRecursive` always attempted the folder's own removal, and `removedTree` was `failureErrno == null && anyRemoved && !anyUnresolvable`, so an emptied, extraction-created folder came away and was reported.
  - **Refutation attempts that failed:**
    - Nothing in the extraction path refuses entries under `Android/`.
    - `isWithinAllowedRoots` (`FileRepository.kt:2022`) accepts a storage root as the target.
    - No app code creates `Android/obb`.
  - **Unverified assumptions:** two platform behaviors come from AOSP knowledge and were not run on a device:
    - an SD card has no `Android/obb` until some app requests one;
    - the storage layer lets an All Files Access app create and remove `<card>/Android/obb` itself.
  - **Contract evidence:**
    - The commit's new test `a failed extraction does not report a tree holding a folder it left in place` (`FileRepositoryTest.kt` ~3792) pins this outcome.
    - It rests on the premise that such a folder can never be removed, which is false for a folder this extraction just made.
    - The rollback's own comment states the goal: a failed extraction "never leaves extracted or half-written files behind".
  - **MediaStore impact is small:** extracted paths are scanned in batches of 500, so a typical one- or two-file OBB archive has registered nothing before it fails.
- **Suggested fix:**
  - Let the rollback walk skip the protected-folder rule, for example with a parameter on `deleteRecursive` that `removedTree` sets, because everything the rollback walks is something this extraction created.
  - The safety property still holds: a folder whose removal fails returns an errno, so `removedTree` still answers false and nothing still on disk reaches the MediaStore prefix delete.
  - The user's own delete must keep the rule.
  - The test above needs its expectation reversed for a folder the extraction created.

### [b/resource-and-configuration-parity/localization/rtl-mark-missing-on-latin-first-labels] Right-to-left mark added to some Urdu strings but not to labels of the same shape

- **Location:** `app/src/main/res/values-ur/strings.xml:132`
  - Related: `values-ur/strings.xml:133` (`storage_usb_drive`), `values-ur/strings.xml:221` (`info_gps_coordinates`), `values-ar/strings.xml:340` (`info_target_sdk`)
  - Where the commit applied the fix: `values-ur/strings.xml:48`, `:52`, `:65`, and `other_apps_description`, `share_app_message`
  - Where the labels are shown: `data/source/AndroidStorageSource.kt:82-84`, `ui/screens/iteminfo/ItemInfoScreen.kt:705-709`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. Only Urdu and Arabic users see it, on the Home storage list when an SD card or USB drive is attached, and on the info screen of a GPS-tagged image or an APK.
- **Defect:** This is an incomplete fix. The commit prefixes a right-to-left mark (`‏`) to Urdu strings whose first letter-like character is Latin, so they lay out right-to-left. That covers five older strings (`apk_install_error`, `apk_permission_message`, `delete_error_permission`, `other_apps_description`, `share_app_message`) and the two new delete strings. Three Urdu strings of the same mixed shape stay without it: `storage_sd_card` "SD کارڈ", `storage_usb_drive` "USB ڈرائیو" and `info_gps_coordinates` "GPS کوآرڈینیٹس". So does the Arabic `info_target_sdk` "SDK المستهدف". These still lay out left-to-right, so a right-to-left reader meets the words in reverse ("card SD", "coordinates GPS").
- **Trigger:** App language Urdu (or Arabic for `info_target_sdk`). Then open Home with an SD card or USB drive mounted, or open item info for a photo with GPS data or an APK.
- **Evidence / verification:**
  - A script that classifies each string's first strong bidi character found exactly these four mixed-direction strings left at HEAD, out of eight such Urdu strings at the baseline.
  - All four render through Compose `Text` with no explicit text direction: the storage name via `getString` in `AndroidStorageSource.name()`, the labels via `InfoRow`'s plain `labelMedium`.
  - No theme, typography or CompositionLocal in `app/src/main/java` sets a text direction. With the pinned Compose BOM, an unspecified direction resolves from the first strong character, which is Latin here.
  - The refutation pass could not break the rendering claim. It argued the strings are older than this commit, which is what makes this an incomplete fix rather than an introduced defect.
  - Not observed on a device.
- **Suggested fix:** Prefix `‏` to those three Urdu strings and the Arabic one, as the commit did for the others. No code compares these resources: the volume-kind lookup in `VolumeInfo.kt` reads Android's own system strings. So the change only affects display.

## Summary

By severity: Low (2 findings).

By confidence: Medium (1 finding), Low (1 finding).

| Severity | High | Medium | Low |
|----------|------|--------|-----|
| Critical | 0    | 0      | 0   |
| High     | 0    | 0      | 0   |
| Medium   | 0    | 0      | 0   |
| Low      | 0    | 1      | 1   |

By Likelihood: High (0 findings), Medium (0 findings), Low (2 findings).
