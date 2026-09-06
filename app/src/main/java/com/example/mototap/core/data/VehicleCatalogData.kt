package com.example.mototap.core.data

/**
 * Vehicle make/model catalog for MotoTap pickers.
 * Ported from web js/vehicleCatalogData.js — keep make/model names aligned so
 * Firestore vehicle price keys ("Make", "Make:Model") match across platforms.
 */
object VehicleCatalogData {

    const val VERSION = 1

    data class VehicleTypeOption(val id: String, val name: String)

    private data class Make(val name: String, val models: List<String>)
    private data class Category(val id: String, val name: String, val makes: List<Make>)

    private val categories: List<Category> = listOf(
        Category(
            "hatchbacks", "Hatchbacks (Small Cars)", listOf(
                Make("Toyota", listOf("Aqua", "Passo", "Vitz", "Ractis", "Yaris", "Blade", "Raum", "IST", "Porte", "Spade")),
                Make("Honda", listOf("Fit", "Jazz", "Brio")),
                Make("Nissan", listOf("Note", "March", "Micra", "Tiida")),
                Make("Mazda", listOf("Demio", "Verisa")),
                Make("Suzuki", listOf("Swift", "Alto", "Celerio", "Wagon R", "Splash", "Baleno", "Ignis")),
                Make("Mitsubishi", listOf("Mirage", "Colt")),
                Make("Volkswagen", listOf("Polo", "Golf")),
                Make("Peugeot", listOf("208", "207")),
                Make("Kia", listOf("Picanto", "Rio Hatchback")),
                Make("Hyundai", listOf("i10", "i20")),
            )
        ),
        Category(
            "sedans", "Sedans (Saloons)", listOf(
                Make("Toyota", listOf("Premio", "Allion", "Corolla", "Axio", "Belta", "Camry", "Crown", "Mark X", "Avensis", "Sai", "Crown Majesta")),
                Make("Nissan", listOf("Sunny", "Almera", "Bluebird", "Bluebird Sylphy", "Sylphy", "Teana", "Fuga", "Skyline")),
                Make("Honda", listOf("Civic", "Accord", "Grace", "Insight")),
                Make("Mazda", listOf("Axela", "Atenza", "Familia")),
                Make("Subaru", listOf("Legacy B4", "Impreza G4")),
                Make("Mitsubishi", listOf("Galant", "Lancer", "Attrage")),
                Make("Mercedes-Benz", listOf("A-Class Sedan", "C-Class", "CLA", "E-Class", "S-Class")),
                Make("BMW", listOf("1 Series Sedan", "3 Series", "5 Series", "7 Series")),
                Make("Audi", listOf("A3", "A4", "A5", "A6", "A8")),
                Make("Volkswagen", listOf("Passat", "Jetta")),
                Make("Lexus", listOf("IS", "ES", "LS", "GS")),
            )
        ),
        Category(
            "station-wagons", "Station Wagons", listOf(
                Make("Toyota", listOf("Corolla Fielder", "Probox", "Succeed", "Caldina", "Wish")),
                Make("Nissan", listOf("Wingroad", "AD Van", "Stagea")),
                Make("Subaru", listOf("Legacy Wagon", "Levorg", "Outback")),
                Make("Mazda", listOf("Atenza Wagon")),
                Make("Volkswagen", listOf("Golf Variant", "Passat Variant")),
                Make("Volvo", listOf("V40", "V60", "V90")),
            )
        ),
        Category(
            "suvs", "SUVs", listOf(
                Make("Toyota", listOf("RAV4", "Harrier", "Land Cruiser", "Prado", "Fortuner", "Rush", "Kluger", "Vanguard", "C-HR", "Raize")),
                Make("Nissan", listOf("X-Trail", "Qashqai", "Dualis", "Patrol", "Murano", "Juke", "Kicks")),
                Make("Honda", listOf("CR-V", "HR-V", "Vezel", "ZR-V")),
                Make("Mazda", listOf("CX-3", "CX-5", "CX-8", "CX-9", "CX-30", "CX-60")),
                Make("Subaru", listOf("Forester", "XV", "Crosstrek", "Outback", "Ascent")),
                Make("Mitsubishi", listOf("Pajero", "Pajero Sport", "Outlander", "Eclipse Cross", "RVR")),
                Make("Ford", listOf("Everest", "Escape", "Explorer", "Edge", "Bronco")),
                Make("Hyundai", listOf("Tucson", "Santa Fe", "Palisade", "Creta")),
                Make("Kia", listOf("Sportage", "Sorento", "Seltos", "Telluride")),
                Make("Volkswagen", listOf("Tiguan", "Touareg", "T-Cross")),
                Make("Land Rover", listOf("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Sport", "Evoque", "Velar")),
                Make("Jeep", listOf("Wrangler", "Grand Cherokee", "Compass", "Renegade")),
                Make("Lexus", listOf("UX", "NX", "RX", "GX", "LX")),
            )
        ),
        Category(
            "pickup-trucks", "Pickup Trucks", listOf(
                Make("Toyota", listOf("Hilux", "Land Cruiser Pickup")),
                Make("Isuzu", listOf("D-Max")),
                Make("Ford", listOf("Ranger", "F-150")),
                Make("Nissan", listOf("Navara", "Hardbody")),
                Make("Mitsubishi", listOf("L200", "Triton")),
                Make("Mazda", listOf("BT-50")),
                Make("Volkswagen", listOf("Amarok")),
                Make("Mahindra", listOf("Pik Up")),
                Make("GWM", listOf("P-Series", "Steed")),
            )
        ),
        Category(
            "vans-minivans", "Vans & Minivans", listOf(
                Make("Toyota", listOf("Hiace", "Noah", "Voxy", "Alphard", "Vellfire", "Esquire", "Sienta", "Wish", "Isis")),
                Make("Nissan", listOf("Serena", "Caravan", "NV200", "Elgrand")),
                Make("Honda", listOf("Stepwgn", "Freed", "Odyssey", "Mobilio")),
                Make("Mazda", listOf("Bongo", "Premacy")),
                Make("Mitsubishi", listOf("Delica")),
                Make("Suzuki", listOf("Every")),
            )
        ),
        Category(
            "commercial-trucks", "Commercial Trucks", listOf(
                Make("Isuzu", listOf("N-Series", "F-Series", "Giga")),
                Make("Hino", listOf("300", "500", "700")),
                Make("Mitsubishi Fuso", listOf("Canter", "Fighter", "Super Great")),
                Make("Mercedes-Benz", listOf("Actros", "Atego", "Axor")),
                Make("Scania", listOf("P-Series", "G-Series", "R-Series")),
                Make("Volvo", listOf("FH", "FM", "FMX")),
                Make("MAN", listOf("TGS", "TGX")),
                Make("FAW", listOf("J5", "J6")),
                Make("Sinotruk", listOf("Howo", "Tata", "LPT Series")),
            )
        ),
        Category(
            "buses-coaches", "Buses & Coaches", listOf(
                Make("Toyota", listOf("Coaster")),
                Make("Nissan", listOf("Civilian")),
                Make("Isuzu", listOf("FRR", "NQR")),
                Make("Hino", listOf("AK", "RK", "Rainbow")),
                Make("Scania", listOf("Touring Coach")),
                Make("Volvo", listOf("9700", "9800")),
                Make("Yutong", listOf("ZK Series")),
                Make("Zhongtong", listOf("LCK Series")),
                Make("King Long", listOf("XMQ Series")),
            )
        ),
        Category(
            "luxury-cars", "Luxury Cars", listOf(
                Make("Mercedes-Benz", listOf("S-Class", "CLS", "Maybach S-Class", "G-Class")),
                Make("BMW", listOf("7 Series", "X5", "X6", "X7", "XM")),
                Make("Audi", listOf("A8", "Q7", "Q8")),
                Make("Lexus", listOf("LS", "LX", "GX", "RX")),
                Make("Porsche", listOf("Cayenne", "Macan", "Panamera", "Taycan")),
                Make("Maserati", listOf("Levante", "Ghibli", "Quattroporte")),
                Make("Bentley", listOf("Bentayga", "Flying Spur", "Continental GT")),
                Make("Rolls-Royce", listOf("Ghost", "Phantom", "Cullinan")),
            )
        ),
        Category(
            "sports-cars", "Sports Cars", listOf(
                Make("Toyota", listOf("Supra", "GR86", "MR2")),
                Make("Nissan", listOf("GT-R", "350Z", "370Z")),
                Make("Subaru", listOf("BRZ")),
                Make("Mazda", listOf("MX-5 Miata")),
                Make("Ford", listOf("Mustang")),
                Make("Chevrolet", listOf("Camaro", "Corvette")),
                Make("Porsche", listOf("718 Cayman", "911")),
            )
        ),
        Category(
            "electric-vehicles", "Electric Vehicles (EVs)", listOf(
                Make("BYD", listOf("Atto 3", "Dolphin", "Seal", "Sealion", "Yuan Plus")),
                Make("Tesla", listOf("Model 3", "Model Y", "Model S", "Model X")),
                Make("Nissan", listOf("Leaf", "Ariya")),
                Make("Hyundai", listOf("Kona Electric", "Ioniq 5", "Ioniq 6")),
                Make("Kia", listOf("EV6", "EV9", "Niro EV")),
                Make("BMW", listOf("i3", "i4", "i5", "i7", "iX")),
                Make("Mercedes-Benz", listOf("EQA", "EQB", "EQE", "EQS")),
                Make("Volkswagen", listOf("ID.3", "ID.4", "ID.5")),
            )
        ),
        Category(
            "motorcycles", "Motorcycles", listOf(
                Make("Bajaj", listOf("Boxer BM100", "Boxer BM125", "Boxer X150")),
                Make("TVS", listOf("HLX 100", "Star", "Apache")),
                Make("Honda", listOf("Ace CB125", "CG125", "XR150L")),
                Make("Yamaha", listOf("Crux", "YBR125", "FZ", "MT-15")),
                Make("Suzuki", listOf("GN125", "Gixxer")),
                Make("Hero", listOf("Splendor", "Hunk")),
                Make("Haojue", listOf("HJ125")),
                Make("Dayun", listOf("DY125")),
                Make("Senke", listOf("SK125")),
            )
        ),
        Category(
            "three-wheelers", "Three-Wheelers (Tuk Tuks)", listOf(
                Make("Bajaj", listOf("RE")),
                Make("TVS", listOf("King Deluxe", "King Cargo")),
                Make("Piaggio", listOf("Ape City", "Ape Xtra")),
                Make("Mahindra", listOf("Alfa")),
                Make("Lohia", listOf("Narain Cargo")),
            )
        ),
    )

    /** make (lowercase) -> display name + merged, sorted models */
    private val index: Map<String, Pair<String, List<String>>> = buildIndex()

    private fun buildIndex(): Map<String, Pair<String, List<String>>> {
        val byLowerMake = linkedMapOf<String, Pair<String, LinkedHashSet<String>>>()
        categories.forEach { category ->
            category.makes.forEach { make ->
                val name = make.name.trim()
                if (name.isEmpty()) return@forEach
                val key = name.lowercase()
                val entry = byLowerMake.getOrPut(key) { name to LinkedHashSet() }
                make.models.forEach { model ->
                    val modelName = model.trim()
                    if (modelName.isNotEmpty()) entry.second.add(modelName)
                }
            }
        }
        return byLowerMake.mapValues { (_, value) ->
            value.first to value.second.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    fun vehicleTypeOptions(): List<VehicleTypeOption> =
        categories.map { VehicleTypeOption(it.id, it.name) }

    fun knownVehicleTypeIds(): Set<String> = categories.map { it.id }.toSet()

    /** Category IDs whose catalog includes this make (and model, when provided). */
    fun categoryIdsForVehicle(make: String?, model: String?): List<String> {
        val makeKey = make?.trim()?.lowercase().orEmpty()
        if (makeKey.isEmpty()) return emptyList()
        val modelKey = model?.trim()?.lowercase().orEmpty()
        return categories.mapNotNull { category ->
            val matchesMake = category.makes.any { entry ->
                if (entry.name.trim().lowercase() != makeKey) return@any false
                if (modelKey.isEmpty()) true
                else entry.models.any { it.trim().lowercase() == modelKey }
            }
            if (matchesMake) category.id else null
        }
    }

    /** Sorted list of all makes in the catalog. */
    fun makeNames(): List<String> =
        index.values.map { it.first }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Sorted models for a make (case-insensitive). */
    fun modelsForMake(make: String?): List<String> {
        val key = make?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return emptyList()
        return index[key]?.second ?: emptyList()
    }

    /** Resolve a stored make to the catalog display name, if possible. */
    fun resolveMake(make: String?): String {
        val key = make?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return ""
        return index[key]?.first ?: make!!.trim()
    }

    /** Resolve a stored model for a make to the catalog display name, if possible. */
    fun resolveModel(make: String?, model: String?): String {
        val target = model?.trim().orEmpty()
        if (target.isEmpty()) return ""
        val models = modelsForMake(resolveMake(make))
        val targetLower = target.lowercase()
        return models.firstOrNull { it.lowercase() == targetLower } ?: target
    }
}
