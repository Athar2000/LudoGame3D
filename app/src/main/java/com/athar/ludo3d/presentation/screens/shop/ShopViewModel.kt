package com.athar.ludo3d.presentation.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.model.SHOP_CATALOG
import com.athar.ludo3d.domain.model.ShopItem
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.RewardsRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class ShopUiState(
    val catalog: List<ShopItem> = SHOP_CATALOG,
    val ownedItemIds: Set<String> = emptySet(),
    val coins: Int = 0,
    val purchasingItemId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    userProfileRepository: UserProfileRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _purchasingItemId = MutableStateFlow<String?>(null)

    private val profile = userProfileRepository.observeProfile(authRepository.currentUser()?.uid ?: "")

    val uiState: StateFlow<ShopUiState> = combine(profile, _purchasingItemId, _errorMessage) { p, purchasing, error ->
        ShopUiState(
            ownedItemIds = p?.ownedItems ?: emptySet(),
            coins = p?.coins ?: 0,
            purchasingItemId = purchasing,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopUiState())

    fun purchase(item: ShopItem) {
        viewModelScope.launch {
            _purchasingItemId.value = item.id
            _errorMessage.value = null
            rewardsRepository.purchaseItem(item.id)
                .onFailure { e -> _errorMessage.value = e.message ?: "Purchase failed" }
            _purchasingItemId.value = null
        }
    }
}
