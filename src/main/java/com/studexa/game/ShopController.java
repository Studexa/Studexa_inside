package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.game.dto.ShopItemDto;
import com.studexa.game.dto.ShopPurchaseResult;
import com.studexa.game.dto.UserShopItemDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public List<ShopItemDto> items(@AuthenticationPrincipal Jwt jwt) {
        return shopService.listItems();
    }

    @GetMapping("/owned")
    public List<UserShopItemDto> owned(@AuthenticationPrincipal Jwt jwt) {
        return shopService.listOwned(CurrentUser.id(jwt));
    }

    /**
     * Buy a shop item. Server validates coins and ownership.
     */
    @PostMapping("/buy")
    public ShopPurchaseResult buy(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BuyItemRequest request) {
        return shopService.buyItem(CurrentUser.id(jwt), request.shopItemId());
    }

    public record BuyItemRequest(@NotBlank String shopItemId) {
    }
}
