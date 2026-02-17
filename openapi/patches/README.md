# OpenAPI Patches

This directory contains patches applied after code generation to fix type issues caused by ambiguous `allOf` compositions in the upstream OpenAPI spec.

## Why Patches Are Needed

The OpenAPI generator produces incorrect types for certain fields:

- **Pagination links** (`first`, `last`, `prev`, `next`) are generated as `kotlin.Any?` instead of `java.net.URI?`
- **Metadata fields** (`self`, `resourceName`) are generated as `kotlin.Any?` instead of `java.net.URI?`
- **Spec fields** are generated as `kotlin.Any` instead of properly typed specs (`Scaffoldv1TemplateSpec`, `Scaffoldv1TemplateCollectionSpec`)

These patches correct the types to match the actual API contract.

## Patch Files

- `fix-pagination-metadata-types.patch` - Fixes pagination link types in list metadata classes
- `fix-object-metadata-types.patch` - Fixes metadata `self` and `resourceName` types
- `fix-template-spec-types.patch` - Fixes template spec types
- `fix-template-collection-spec-types.patch` - Fixes template collection spec types

## Regenerating Patches

If the upstream OpenAPI spec changes and patches no longer apply cleanly:

1. **Clean the generated code:**
   ```bash
   rm -rf gen/
   ```

2. **Temporarily disable patches in build.gradle.kts:**
   Comment out the `doLast` block in the `openApiGenerate` task

3. **Generate unpatched code:**
   ```bash
   ./gradlew openApiGenerate
   ```

4. **Manually fix the type issues** in the generated files under `gen/io/confluent/intellijplugin/scaffold/model/`

5. **Create new patch files:**
   ```bash
   # For each affected file, create a patch showing the diff
   git diff --no-index gen/io/confluent/intellijplugin/scaffold/model/OldFile.kt gen/io/confluent/intellijplugin/scaffold/model/FixedFile.kt > openapi/patches/fix-xxx.patch
   ```

   Or regenerate by comparing against the known bad patterns and creating unified diffs manually.

6. **Update build.gradle.kts** if file paths or patch names change

7. **Test the patches:**
   ```bash
   ./gradlew clean openApiGenerate
   ./gradlew build
   ```

## Applying Patches Manually

To apply patches manually (for testing):

```bash
git apply --directory=gen openapi/patches/fix-pagination-metadata-types.patch
git apply --directory=gen openapi/patches/fix-object-metadata-types.patch
git apply --directory=gen openapi/patches/fix-template-spec-types.patch
git apply --directory=gen openapi/patches/fix-template-collection-spec-types.patch
```

The `--directory=gen` flag ensures patches are applied relative to the `gen/` directory.
