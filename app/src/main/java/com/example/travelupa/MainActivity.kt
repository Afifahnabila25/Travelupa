package com.example.travelupa

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil.compose.rememberAsyncImagePainter
import com.example.travelupa.ui.theme.TravelupaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// --- ENTITAS DATA ---
data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    val gambarResId: Int? = null
)

// --- RUTE NAVIGASI ---
sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")
    object Login : Screen("login")
    object Register : Screen("register")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
    object Gallery : Screen("gallery")
}

class MainActivity : ComponentActivity() {
    // Database diinisialisasi di sini agar bisa dipakai di seluruh aplikasi
    private lateinit var imageDao: ImageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        // Inisialisasi Room Database
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "travelupa-database"
        ).build()
        imageDao = db.imageDao()

        val currentUser = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Kirim imageDao ke navigasi
                    AppNavigation(currentUser = currentUser, imageDao = imageDao)
                }
            }
        }
    }
}

// --- HELPER: SIMPAN GAMBAR DARI FILE ---
fun saveImageLocally(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
    inputStream?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return file.absolutePath
}

// --- HELPER: SIMPAN GAMBAR DARI KAMERA (BITMAP) ---
fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
    val outputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    outputStream.close()
    return Uri.fromFile(file)
}

// --- HELPER: UPLOAD (ROOM + FIRESTORE) ---
fun uploadImageToFirestore(
    firestore: FirebaseFirestore,
    imageDao: ImageDao, // Terima DAO dari luar
    context: Context,
    gambarUri: Uri,
    tempatWisata: TempatWisata,
    onSuccess: (TempatWisata) -> Unit,
    onFailure: (Exception) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 1. Simpan gambar fisik
            val localPath = saveImageLocally(context, gambarUri)

            // 2. Catat di Room Database
            imageDao.insert(ImageEntity(localPath = localPath))

            // 3. Simpan ke Firestore
            val updatedTempat = tempatWisata.copy(gambarUriString = localPath)
            firestore.collection("tempat_wisata")
                .document(updatedTempat.nama)
                .set(updatedTempat)
                .addOnSuccessListener { onSuccess(updatedTempat) }
                .addOnFailureListener { onFailure(it) }

        } catch (e: Exception) {
            onFailure(e)
        }
    }
}

// --- NAVIGASI ---
@Composable
fun AppNavigation(currentUser: FirebaseUser?, imageDao: ImageDao) {
    val navController = rememberNavController()
    val startScreen = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route

    NavHost(navController = navController, startDestination = startScreen) {
        composable(Screen.Greeting.route) {
            GreetingScreen(onStart = { navController.navigate(Screen.Login.route) })
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClicked = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                imageDao = imageDao,
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Greeting.route) {
                        popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                    }
                },
                onGoToGallery = { navController.navigate(Screen.Gallery.route) } // Navigasi ke Galeri
            )
        }
        // Halaman Galeri
        composable(Screen.Gallery.route) {
            GalleryScreen(
                imageDao = imageDao,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// --- HALAMAN UTAMA DENGAN DRAWER (MENU SAMPING) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekomendasiTempatScreen(
    imageDao: ImageDao,
    onBackToLogin: () -> Unit,
    onGoToGallery: () -> Unit
) {
    var daftarTempatWisata by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showDialog by remember { mutableStateOf(false) }

    // State untuk Drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        firestore.collection("tempat_wisata")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    daftarTempatWisata = snapshot.documents.mapNotNull { it.toObject(TempatWisata::class.java) }
                }
            }
    }

    // ModalNavigationDrawer untuk Menu Samping
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Menu Travelupa", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Galeri Foto") },
                    selected = false,
                    icon = { Icon(Icons.Default.Image, null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onGoToGallery()
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    selected = false,
                    icon = { Icon(Icons.Default.ExitToApp, null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onBackToLogin()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Travelupa") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah")
                }
            }
        ) { paddingValues ->
            if (showDialog) {
                TambahTempatDialog(
                    firestore = firestore,
                    imageDao = imageDao,
                    context = context,
                    onDismiss = { showDialog = false },
                    onConfirm = { showDialog = false }
                )
            }
            LazyColumn(contentPadding = paddingValues, modifier = Modifier.padding(16.dp)) {
                items(daftarTempatWisata) { tempat ->
                    TempatItemList(
                        tempat = tempat,
                        onDelete = { firestore.collection("tempat_wisata").document(tempat.nama).delete() }
                    )
                }
            }
        }
    }
}

// --- HALAMAN GALERI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(imageDao: ImageDao, onBack: () -> Unit) {
    // Ambil data dari Room secara Realtime
    val images by imageDao.getAllImages().collectAsState(initial = emptyList())
    var showAddImageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galeri Foto Lokal") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddImageDialog = true }) {
                Icon(Icons.Default.Add, "Tambah Foto")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding).padding(4.dp)
        ) {
            items(images) { image ->
                Image(
                    painter = rememberAsyncImagePainter(model = File(image.localPath)),
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showAddImageDialog) {
            AddImageDialog(
                onDismiss = { showAddImageDialog = false },
                onImageAdded = { uri ->
                    // Simpan ke Room saat foto dipilih dari Galeri/Kamera khusus halaman Galeri
                    scope.launch(Dispatchers.IO) {
                        val localPath = saveImageLocally(context, uri)
                        imageDao.insert(ImageEntity(localPath = localPath))
                    }
                    showAddImageDialog = false
                }
            )
        }
    }
}

// --- DIALOG TAMBAH FOTO (GALERI / KAMERA) ---
@Composable
fun AddImageDialog(onDismiss: () -> Unit, onImageAdded: (Uri) -> Unit) {
    val context = LocalContext.current
    var tempUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher untuk Galeri
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImageAdded(uri)
    }

    // Launcher untuk Kamera
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToUri(context, bitmap)
            onImageAdded(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Sumber Foto") },
        text = { Text("Ambil foto dari kamera atau pilih dari galeri?") },
        confirmButton = {
            Button(onClick = { cameraLauncher.launch(null) }) {
                Text("Kamera")
            }
        },
        dismissButton = {
            Button(onClick = { galleryLauncher.launch("image/*") }) {
                Text("Galeri")
            }
        }
    )
}

// --- UPDATE DIALOG TAMBAH TEMPAT: KONEK KE KAMERA ---
@Composable
fun TambahTempatDialog(
    firestore: FirebaseFirestore,
    imageDao: ImageDao,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }

    // Launcher Galeri
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        showSourceDialog = false
    }
    // Launcher Kamera
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedImageUri = saveBitmapToUri(context, bitmap)
        }
        showSourceDialog = false
    }

    // Dialog Input Data
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Wisata") },
        text = {
            Column {
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama") })
                OutlinedTextField(value = deskripsi, onValueChange = { deskripsi = it }, label = { Text("Deskripsi") })
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(150.dp).background(Color.Gray.copy(0.1f))
                        .clickable { showSourceDialog = true }, // Klik untuk buka pilihan
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        Image(painter = rememberAsyncImagePainter(selectedImageUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text("Pilih/Ambil Foto")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isUploading,
                onClick = {
                    if (nama.isNotEmpty() && deskripsi.isNotEmpty() && selectedImageUri != null) {
                        isUploading = true
                        uploadImageToFirestore(
                            firestore, imageDao, context, selectedImageUri!!, TempatWisata(nama, deskripsi),
                            onSuccess = { isUploading = false; onConfirm() },
                            onFailure = { isUploading = false }
                        )
                    }
                }
            ) {
                if(isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )

    // Dialog Pilihan Sumber (Kamera/Galeri) muncul saat kotak foto diklik
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Ambil Gambar Dari") },
            confirmButton = {
                Button(onClick = { cameraLauncher.launch(null) }) { Text("Kamera") }
            },
            dismissButton = {
                Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Galeri") }
            }
        )
    }
}

@Composable
fun GreetingScreen(onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Selamat Datang di Travelupa!", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onStart, modifier = Modifier.padding(top=16.dp)) { Text("Mulai") }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onRegisterClicked: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Scope untuk menjalankan fungsi suspend (Firebase Auth)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), // Padding agar tidak mepet layar
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Judul Besar
        Text(
            text = "Login Travelupa",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Input Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Input Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Login (Biru Gelap & Lebar)
        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            // Proses Login Firebase di Background
                            withContext(Dispatchers.IO) {
                                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                            }
                            isLoading = false
                            onLoginSuccess()
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = "Login Gagal: ${e.localizedMessage}"
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3F51B5)
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(text = "Login", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Teks Navigasi ke Daftar
        TextButton(onClick = onRegisterClicked) {
            Text(
                text = "Belum punya akun? Daftar disini",
                color = Color(0xFF3F51B5)
            )
        }

        // Pesan Error
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Judul
        Text(
            text = "Daftar Akun Baru",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Input Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Input Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Input Konfirmasi Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Konfirmasi Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Daftar
        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (password == confirmPassword) {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                // Proses Register Firebase
                                withContext(Dispatchers.IO) {
                                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                                }
                                isLoading = false
                                onRegisterSuccess()
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Gagal Daftar: ${e.localizedMessage}"
                            }
                        }
                    } else {
                        errorMessage = "Password tidak sama!"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3F51B5)
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(text = "Daftar", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Teks Navigasi ke Login
        TextButton(onClick = onBackToLogin) {
            Text(
                text = "Sudah punya akun? Login",
                color = Color(0xFF3F51B5)
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
@Composable
fun TempatItemList(tempat: TempatWisata, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Column {
            val painter = if (tempat.gambarUriString != null) {
                if (tempat.gambarUriString.startsWith("/")) rememberAsyncImagePainter(model = File(tempat.gambarUriString))
                else rememberAsyncImagePainter(model = tempat.gambarUriString)
            } else painterResource(id = R.drawable.ic_launcher_background)
            Image(painter = painter, contentDescription = null, modifier = Modifier.height(200.dp).fillMaxWidth(), contentScale = ContentScale.Crop)
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tempat.nama, style = MaterialTheme.typography.titleLarge)
                    Text(tempat.deskripsi, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}