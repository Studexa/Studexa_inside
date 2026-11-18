package com.studexa.game;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.game.dto.ShopItemDto;
import com.studexa.game.dto.ShopPurchaseResult;
import com.studexa.game.dto.UserShopItemDto;

/**
 * Coin shop service. Validates coin balance and ownership on the server.
 * Coins never buy XP, answers, or study advantages - only cosmetics and streak freezes.
 * All balance checks and mutations happen on a single locked {@link GameState}
 * row so concurrent purchases cannot double-spend coins (BUG-D07).
 */
@Service
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final UserShopItemRepository userShopItemRepository;
    private final GameStateRepository gameStateRepository;
    private final GameProperties properties;
    private final PetService petService;
    private final Clock clock;

    public ShopService(ShopItemRepository shopItemRepository,
            UserShopItemRepository userShopItemRepository, GameStateRepository gameStateRepository,
            GameProperties properties, PetService petService, Clock clock) {
        this.shopItemRepository = shopItemRepository;
        this.userShopItemRepository = userShopItemRepository;
        this.gameStateRepository = gameStateRepository;
        this.properties = properties;
        this.petService = petService;
        this.clock = clock;
    }

    /**
     * List all active shop items.
     */
    @Transactional(readOnly = true)
    public List<ShopItemDto> listItems() {
        return shopItemRepository.findByActiveOrderByPriceAsc(true).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Buy a shop item. Validates coin balance, prevents duplicate purchases,
     * and deducts coins from the student's game state. All checks happen on a
     * single locked {@link GameState} row so concurrent purchases cannot
     * double-spend coins (BUG-D07).
     */
    @Transactional
    public ShopPurchaseResult buyItem(UUID userId, String shopItemId) {
        ShopItem item = shopItemRepository.findById(shopItemId)
                .orElseThrow(() -> ApiException.notFound("Shop item not found."));

        if (!item.isActive()) {
            throw ApiException.badRequest("ITEM_NOT_ACTIVE", "This item is not available.");
        }

        // Lock the game state row FIRST so the ownership and coin-balance
        // checks are atomic against concurrent purchases.
        GameState state = gameStateRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.badRequest("NO_GAME_STATE", "Game state not found."));

        boolean isFreeze = "freeze".equals(item.getKind());
        // Consumable freezes may be bought more than once, up to the inventory
        // cap. Permanent cosmetics and themes remain single-purchase items.
        if (!isFreeze && userShopItemRepository.existsByUserIdAndShopItemId(userId, shopItemId)) {
            throw ApiException.badRequest("ALREADY_OWNED", "You already own this item.");
        }

        // A student may hold at most maxFreezesHeld streak freezes.
        if ("freeze".equals(item.getKind()) && state.getStreakFreezes() >= properties.maxFreezesHeld()) {
            throw ApiException.badRequest("MAX_FREEZES", "You already hold the maximum number of streak freezes.");
        }

        // Validate coin balance under the lock.
        if (state.getCoins() < item.getPrice()) {
            throw ApiException.badRequest("NOT_ENOUGH_COINS", "Not enough coins.");
        }

        // Record the purchase.
        UserShopItem purchase = new UserShopItem(userId, shopItemId, clock.instant());
        userShopItemRepository.save(purchase);

        // Deduct coins and apply freeze under the same lock.
        if (isFreeze) {
            state.setStreakFreezes(state.getStreakFreezes() + 1);
        }
        state.setCoins(state.getCoins() - item.getPrice());
        state.setUpdatedAt(clock.instant());
        gameStateRepository.save(state);

        return new ShopPurchaseResult(shopItemId, item.getPrice(), state.getCoins(), isFreeze);
    }

    /**
     * List items owned by the user.
     */
    @Transactional(readOnly = true)
    public List<UserShopItemDto> listOwned(UUID userId) {
        return userShopItemRepository.findByUserId(userId).stream()
                .map(u -> new UserShopItemDto(u.getId(), u.getShopItemId(), u.getPurchasedAt()))
                .collect(Collectors.toList());
    }

    /* ---------------------------------------------------------------- */

    private ShopItemDto toDto(ShopItem item) {
        return new ShopItemDto(
                item.getId(), item.getNameEn(), item.getNameKm(),
                item.getKind(), item.getPrice(), item.getAccessoryType(), item.isActive());
    }
}
