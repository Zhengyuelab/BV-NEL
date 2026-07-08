# V6 record management update

This version keeps the underlying BV-NEL measurement workflow and instrument communication logic unchanged. The update mainly affects the result-record management page.

## Added functions

1. Save a completed BV-NEL result with a sample name.
2. Store up to 9 records in the current batch.
3. Delete individual records from the record page.
4. Clear all records after export or before starting a new batch.
5. Export all saved records as a TXT file.
6. Enter a custom TXT filename before export.
7. Sanitize invalid filename characters automatically.

## Main source files involved

- `MainViewModel.kt`: save, delete, clear, and TXT export logic.
- `MainScreen.kt`: record page, Delete buttons, Clear button, export dialog, and record display UI.
- `Models.kt`: `TestRecord` and user-intent models.

The measurement sequence, fitting logic, timing parameters, and hardware protocol are not changed by this UI/record-management update.
