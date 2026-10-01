---
name: adding-a-data-field
description: Adding, renaming or removing a value a rider can put on a watch screen - catalogue, renderer, 15 locales, coverage test, layout migration. Use for any change to the data fields.
---

# Adding a data field

A field is not done when it renders. It is done when it renders, is translated,
is fed by something real, and is accounted for by the coverage test.

## The steps

1. **`core/.../field/FieldCatalogue.kt`** - add a `DataFieldDef` with a stable
   id. Ids are the contract: they are what a saved layout stores and what the
   race-stats server sends. Pick the category, and restrict `disciplines` only
   if the field is meaningless elsewhere.
2. **`core/.../field/FieldValues.kt`** - render it. Fields are grouped by how
   they are formatted rather than handled one by one, so a new speed field
   should read correctly the moment it joins the catalogue. Add a branch only
   for a genuinely new shape.
3. **`core/.../format/FieldFormatter.kt`** - if the shape is new. One function
   per shape of value, deliberately: a formatter whose output depends on a
   boolean flag renders the wrong field wrongly.
4. **All 15 locales** under `localization/src/main/res/values*/strings.xml`, as
   `field_<id>`, plus the mapping in `Labels.kt`. `LabelsTest` fails otherwise.
5. **`FieldCoverageTest`** - either list the id in `produced`, or add it to
   `notYet` with the reason it cannot be filled yet. That list is work
   outstanding, not an excuse: a field a rider can place and watch stay empty is
   a promise the app is not keeping. Twenty-five such fields were deleted from
   the catalogue outright rather than left there.

## Renaming a field

Add the old id to `FieldCatalogue.renames`. Layouts decode through
`SyncContract`, which runs every slot through `FieldCatalogue.currentId`, so one
entry migrates both the phone's saved layout and the watch's copy. Without it, a
rename is indistinguishable from a deletion: the slot goes quiet on the next
ride and the rider has to work out which of ten slots stopped.

Only map a field that is genuinely the same measurement under a better name. If
the *meaning* changed, do not map it - carrying the layout across would show the
rider something they did not choose.

## Removing a field

Delete it from the catalogue. `FieldValues.snapshot` returns a map, so an id the
catalogue no longer knows resolves to `null` and the screen renders an empty
slot rather than crashing. There is a test for exactly this, because riders have
layouts saved from before the removal.

## Signs

A signed field follows the sport, not the machine. On a result sheet `+2:48`
means two minutes forty-eight **slower**. The segment gap fields use that
convention, and a rider reads the sign before the number. If two fields describe
one quantity - a gap in seconds and the same gap in metres - they must agree.
