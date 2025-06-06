package com.example.ynovente.ui.screens.myproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ynovente.data.model.Offer
import com.example.ynovente.data.model.FinishedOfferDisplay
import com.example.ynovente.data.repository.FirebaseOfferRepository
import com.example.ynovente.data.repository.FirebaseUserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime

class MyProductsViewModel(
    private val offerRepository: FirebaseOfferRepository,
    private val userRepository: FirebaseUserRepository
) : ViewModel() {
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    val myOffers: StateFlow<List<Offer>> = offerRepository
        .getOffersFlow()
        .map { offers -> offers.filter { it.userId == currentUserId } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val finishedOffers: StateFlow<List<Offer>> = myOffers
        .map { offers ->
            offers.filter { offer ->
                try {
                    LocalDateTime.parse(offer.endDate).isBefore(LocalDateTime.now())
                } catch (_: Exception) {
                    false
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeOffers: StateFlow<List<Offer>> = myOffers
        .map { offers ->
            offers.filter { offer ->
                try {
                    LocalDateTime.parse(offer.endDate).isAfter(LocalDateTime.now())
                } catch (_: Exception) {
                    true
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Liste enrichie pour affichage
    @OptIn(ExperimentalCoroutinesApi::class)
    val finishedOffersWithWinner: StateFlow<List<FinishedOfferDisplay>> =
        finishedOffers
            .flatMapLatest { offers ->
                if (offers.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(offers.map { offer ->
                    offerRepository.getBidsForOfferFlow(offer.id).map { bids ->
                        val bestBid = bids.maxByOrNull { it.amount }
                        offer to bestBid
                    }
                }) { list -> list.toList() }
            }
            .flatMapLatest { offerBidList ->
                if (offerBidList.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(offerBidList.map { (offer, bestBid) ->
                    flow {
                        var winnerEmail: String? = null
                        if (bestBid != null) {
                            winnerEmail = userRepository.getUserById(bestBid.userId)?.email
                        }
                        emit(FinishedOfferDisplay(offer, bestBid, winnerEmail))
                    }
                }) { it.toList() }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}