# Picked up automatically by LineageOS (vendor/lineage/config/common.mk
# inherits vendor/extra/product.mk when it exists).

# Install allowlist read by GanInstallPolicy in the package manager.
PRODUCT_COPY_FILES += \
    vendor/extra/gan/allowed_packages.txt:$(TARGET_COPY_OUT_SYSTEM)/etc/gan/allowed_packages.txt
