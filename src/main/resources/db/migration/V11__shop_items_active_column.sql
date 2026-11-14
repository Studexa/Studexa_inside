-- Step 14: align shop_items column with the ShopItem entity (field maps to `active`).
-- Migration V8 created the column as `is_active`; rename it to `active`.
alter table shop_items
    rename column is_active to active;
