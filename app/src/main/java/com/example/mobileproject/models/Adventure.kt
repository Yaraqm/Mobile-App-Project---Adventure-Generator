package com.example.mobileproject.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

/**
 * This class represents the nested 'map_point' object in your Firestore documents.
 */
data class MapPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this(0.0, 0.0)
}

/**
 * This is the main data model for a document in your 'locations' collection.
 * Its properties now EXACTLY match the fields in Firestore.
 */
data class Adventure(
    @DocumentId var id: String? = null, // This will automatically capture the document's ID
    var name: String? = null,           // Was 'title'
    var category: String? = null,       // This is new and correct
    var city: String? = null,           // Was 'location'
    var description: String? = null,
    var map_point: MapPoint? = null     // This matches your nested 'map_point' object
) {
    // No-argument constructor required by Firestore to convert documents back into objects
    constructor() : this(null, null, null, null, null, null)
}
