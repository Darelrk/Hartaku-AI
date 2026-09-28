package com.example.data

/**
 * Seed data for default categories.
 * Fixed UUIDs supaya konsisten — bisa di-refer dari kode lain.
 */
object CategorySeeder {

    // Fixed UUIDs untuk default categories
    const val ID_MAKANAN   = "cat_makanan"
    const val ID_TRANSPORT = "cat_transport"
    const val ID_BELANJA   = "cat_belanja"
    const val ID_HIBURAN   = "cat_hiburan"
    const val ID_TAGIHAN   = "cat_tagihan"
    const val ID_INVESTASI = "cat_investasi"
    const val ID_GAJI      = "cat_gaji"
    const val ID_LAINNYA   = "cat_lainnya"
    const val ID_KESEHATAN   = "cat_kesehatan"
    const val ID_PENDIDIKAN  = "cat_pendidikan"
    const val ID_PERAWATAN   = "cat_perawatan"
    const val ID_OLAHRAGA    = "cat_olahraga"
    const val ID_DONASI      = "cat_donasi"
    const val ID_ASURANSI    = "cat_asuransi"
    const val ID_PERBAIKAN   = "cat_perbaikan"
    const val ID_PELIHARAAN  = "cat_peliharaan"
    const val ID_PENJUALAN   = "cat_penjualan"
    const val ID_HADIAH      = "cat_hadiah"

    val DEFAULT_CATEGORIES = listOf(
        Category(
            id = ID_MAKANAN,
            parentId = null,
            name = "Makanan",
            slug = "makanan",
            typeClass = "EXPENSE",
            aliases = """["makan","kopi","bakso","nasi","minum","ngopi","jajan","makan siang","makan malam","kafe","restoran"]""",
            icon = "restaurant",
            color = "#FFD700",
            sortOrder = 1
        ),
        Category(
            id = ID_TRANSPORT,
            parentId = null,
            name = "Transport",
            slug = "transport",
            typeClass = "EXPENSE",
            aliases = """["transport","gojek","grab","bensin","ojek","bus","kereta","taxi","angkot","parkir","toll"]""",
            icon = "directions_car",
            color = "#00BFFF",
            sortOrder = 2
        ),
        Category(
            id = ID_BELANJA,
            parentId = null,
            name = "Belanja",
            slug = "belanja",
            typeClass = "EXPENSE",
            aliases = """["belanja","baju","sepatu","pakaian","shopping","grosir","pasar","laundry","cuci","setrika","tanaman","bunga","hiasan"]""",
            icon = "shopping_bag",
            color = "#00FF7F",
            sortOrder = 3
        ),
        Category(
            id = ID_HIBURAN,
            parentId = null,
            name = "Hiburan",
            slug = "hiburan",
            typeClass = "EXPENSE",
            aliases = """["hiburan","game","film","nonton","netflix","spotify","game online","musik","konser","liburan"]""",
            icon = "sports_esports",
            color = "#9B59B6",
            sortOrder = 4
        ),
        Category(
            id = ID_TAGIHAN,
            parentId = null,
            name = "Tagihan",
            slug = "tagihan",
            typeClass = "EXPENSE",
            aliases = """["tagihan","listrik","air","pulsa","internet","bpjs","telpon","ciclian","sewa"]""",
            icon = "receipt_long",
            color = "#E74C3C",
            sortOrder = 5
        ),
        Category(
            id = ID_INVESTASI,
            parentId = null,
            name = "Investasi",
            slug = "investasi",
            typeClass = "EXPENSE",
            aliases = """["investasi","saham","crypto","kripto","bibit","reksa dana","obligasi","sbn","deposito","emas","invest"]""",
            icon = "trending_up",
            color = "#00FF7F",
            sortOrder = 6
        ),
        Category(
            id = ID_GAJI,
            parentId = null,
            name = "Gaji",
            slug = "gaji",
            typeClass = "INCOME",
            aliases = """["gaji","bonus","freelance","pendapatan","fee","commission","upah","honor"]""",
            icon = "account_balance",
            color = "#2ECC71",
            sortOrder = 1
        ),
        Category(
            id = ID_PENJUALAN,
            parentId = null,
            name = "Penjualan Aset",
            slug = "penjualan-aset",
            typeClass = "INCOME",
            aliases = """["penjualan aset","jual aset","jual mobil","jual rumah","jual barang","penjualan","properti","kendaraan"]""",
            icon = "sell",
            color = "#2196F3",
            sortOrder = 2
        ),
        Category(
            id = ID_HADIAH,
            parentId = null,
            name = "Hadiah & THR",
            slug = "hadiah-thr",
            typeClass = "INCOME",
            aliases = """["hadiah","thr","bonus lebaran","angpao","gift","rejeki","warisan","undian"]""",
            icon = "card_giftcard",
            color = "#CDDC39",
            sortOrder = 3
        ),
        Category(
            id = ID_KESEHATAN,
            parentId = null,
            name = "Kesehatan",
            slug = "kesehatan",
            typeClass = "EXPENSE",
            aliases = """["kesehatan","dokter","obat","klinik","rumah sakit","apotek","medical","checkup","berobat"]""",
            icon = "local_hospital",
            color = "#16A085",
            sortOrder = 7
        ),
        Category(
            id = ID_PENDIDIKAN,
            parentId = null,
            name = "Pendidikan",
            slug = "pendidikan",
            typeClass = "EXPENSE",
            aliases = """["pendidikan","sekolah","kuliah","kursus","kampus","buku","les","seminar","workshop"]""",
            icon = "school",
            color = "#8E44AD",
            sortOrder = 8
        ),
        Category(
            id = ID_PERAWATAN,
            parentId = null,
            name = "Perawatan Diri",
            slug = "perawatan-diri",
            typeClass = "EXPENSE",
            aliases = """["perawatan diri","salon","spa","facial","skincare","salon rambut","grooming","potong rambut","manicure"]""",
            icon = "spa",
            color = "#E91E63",
            sortOrder = 9
        ),
        Category(
            id = ID_OLAHRAGA,
            parentId = null,
            name = "Olahraga",
            slug = "olahraga",
            typeClass = "EXPENSE",
            aliases = """["olahraga","gym","fitness","yoga","lari","jogging","swimming","badminton","futsal","sepeda"]""",
            icon = "fitness_center",
            color = "#FF5722",
            sortOrder = 10
        ),
        Category(
            id = ID_DONASI,
            parentId = null,
            name = "Donasi & Amal",
            slug = "donasi-amal",
            typeClass = "EXPENSE",
            aliases = """["donasi","sedekah","zakat","amal","infak","charity","wakaf","sumbangan"]""",
            icon = "volunteer_activism",
            color = "#009688",
            sortOrder = 11
        ),
        Category(
            id = ID_ASURANSI,
            parentId = null,
            name = "Asuransi & Pajak",
            slug = "asuransi-pajak",
            typeClass = "EXPENSE",
            aliases = """["asuransi","pajak","bpjs","allianz","prudential","premi","polis","insurance"]""",
            icon = "shield",
            color = "#607D8B",
            sortOrder = 12
        ),
        Category(
            id = ID_PERBAIKAN,
            parentId = null,
            name = "Perbaikan Rumah",
            slug = "perbaikan-rumah",
            typeClass = "EXPENSE",
            aliases = """["perbaikan rumah","renovasi","tukang","plumber","cat rumah","service ac","electrician","renovation"]""",
            icon = "home_repair_service",
            color = "#795548",
            sortOrder = 13
        ),
        Category(
            id = ID_PELIHARAAN,
            parentId = null,
            name = "Peliharaan",
            slug = "peliharaan",
            typeClass = "EXPENSE",
            aliases = """["peliharaan","hewan peliharaan","anjing","kucing","pet food","dokter hewan","grooming hewan","pets"]""",
            icon = "pets",
            color = "#FF9800",
            sortOrder = 14
        ),
        Category(
            id = ID_LAINNYA,
            parentId = null,
            name = "Lainnya",
            slug = "lainnya",
            typeClass = "EXPENSE",
            aliases = """["lainnya","other","lain","etc","dll"]""",
            icon = "category",
            color = "#95A5A6",
            sortOrder = 99
        )

    )

    /**
     * Seed hanya kalau TIDAK ADA kategori sama sekali — termasuk yang sudah
     * di-soft-delete.
     *
     * Sebelumnya hanya menghitung kategori aktif, sehingga bila pengguna
     * mengarsipkan semua kategorinya, start berikutnya akan berjalan lagi dan
     * `insertAll` memakai `OnConflictStrategy.REPLACE` — kategori default yang
     * sebelumnya sudah dikustomisasi tertimpa, sementara kategori kustom miliknya
     * tetap terkubur dan sulit dipulihkan.
     */
    suspend fun seedIfEmpty(repository: CategoryRepository) {
        if (repository.countIncludingDeleted() == 0) {
            repository.insertAll(DEFAULT_CATEGORIES)
        }
    }
}