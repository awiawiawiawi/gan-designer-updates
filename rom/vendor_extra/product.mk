# Picked up automatically by LineageOS (vendor/lineage/config/common.mk
# inherits vendor/extra/product.mk when it exists).

# Install allowlist used until the first policy arrives from the server.
PRODUCT_COPY_FILES += \
    vendor/extra/gan/allowed_packages.txt:$(TARGET_COPY_OUT_SYSTEM)/etc/gan/allowed_packages.txt

# Remote management: server address, device token and the public key that
# policies must be signed with. Created by "gan_server.py init".
ifneq ($(wildcard vendor/extra/gan/device_config.json),)
PRODUCT_COPY_FILES += \
    vendor/extra/gan/device_config.json:$(TARGET_COPY_OUT_SYSTEM)/etc/gan/device_config.json
PRODUCT_PACKAGES += GanAgent
endif
