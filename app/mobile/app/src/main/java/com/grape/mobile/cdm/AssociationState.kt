package com.grape.mobile.cdm

sealed class AssociationState {
    object Unassociated : AssociationState()
    object Associating : AssociationState()
    data class Associated(
        val deviceMac: String,
        val displayName: String
    ) : AssociationState()
    data class Failed(
        val reason: String
    ) : AssociationState()
}