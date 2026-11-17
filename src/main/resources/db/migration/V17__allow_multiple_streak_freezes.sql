ALTER TABLE user_shop_items
    DROP CONSTRAINT IF EXISTS user_shop_items_user_id_shop_item_id_key;

CREATE UNIQUE INDEX idx_user_shop_items_unique_permanent
    ON user_shop_items (user_id, shop_item_id)
    WHERE shop_item_id <> 'freeze';
