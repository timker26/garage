package com.example.garage

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.garage.data.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = GarageDb.get(applicationContext).dao()
        setContent { MaterialTheme { AppNav(dao) } }
    }
}

@Composable
fun AppNav(dao: GarageDao) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "garage") {
        composable("garage") { GarageScreen(dao) { carId -> nav.navigate("car/$carId") } }
        composable("car/{carId}") { back ->
            CarScreen(dao, back.arguments!!.getString("carId")!!.toLong(),
                onOpen = { nav.navigate("to/$it") }, onBack = { nav.popBackStack() })
        }
        composable("to/{id}") { back ->
            MaintenanceScreen(dao, back.arguments!!.getString("id")!!.toLong()) { nav.popBackStack() }
        }
    }
}

// ── ЭКРАН 1: ГАРАЖ ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(dao: GarageDao, onOpenCar: (Long) -> Unit) {
    val cars by dao.cars().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Гараж", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Добавить") }
        }
    ) { padding ->
        if (cars.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нажмите +, чтобы добавить машину", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2),
                Modifier.padding(padding).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cars, key = { it.id }) { car ->
                    Card(onClick = { onOpenCar(car.id) }, elevation = CardDefaults.cardElevation(4.dp)) {
                        Column {
                            CarPhoto(car, Modifier.fillMaxWidth().height(110.dp))
                            Column(Modifier.padding(10.dp)) {
                                Text("${car.brand} ${car.model}", fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(car.vin, fontSize = 12.sp, color = Color.Gray,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddCarDialog(dao) { showAdd = false }
}

@Composable
fun CarPhoto(car: Car, modifier: Modifier) {
    val file = car.photoPath?.let { File(it) }
    if (file != null && file.exists()) {
        AsyncImage(file, null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Color(0xFFE4E8EF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = Color.Gray, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
fun AddCarDialog(dao: GarageDao, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photoUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая машина") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(brand, { brand = it }, label = { Text("Марка") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("Модель") }, singleLine = true)
                OutlinedTextField(vin, { vin = it }, label = { Text("VIN") }, singleLine = true)
                OutlinedButton(onClick = { pickPhoto.launch("image/*") }) {
                    Text(if (photoUri == null) "Выбрать фото машины" else "Фото выбрано")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (brand.isBlank()) return@TextButton
                scope.launch {
                    val id = dao.addCar(Car(brand = brand.trim(), model = model.trim(), vin = vin.trim()))
                    photoUri?.let { uri ->
                        val dest = File(context.filesDir, "car_$id.jpg")
                        context.contentResolver.openInputStream(uri)?.use { src ->
                            dest.outputStream().use { src.copyTo(it) }
                        }
                        dao.setCarPhoto(id, dest.absolutePath)
                    }
                    onDismiss()
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ── ЭКРАН 2: БЛОКИ ТО ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(dao: GarageDao, carId: Long, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val list by dao.maintenances(carId).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Техобслуживание") },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Добавить ТО") }
        }
    ) { padding ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Пока нет ни одного ТО. Нажмите +", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2),
                Modifier.padding(padding).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { m ->
                    Card(onClick = { onOpen(m.id) }, elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(m.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Пробег: ${m.mileage} км", fontSize = 13.sp)
                            Text(dateFormat.format(Date(m.date)), fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddMaintenanceDialog(dao, carId, list.size + 1) { showAdd = false }
}

@Composable
fun AddMaintenanceDialog(dao: GarageDao, carId: Long, nextNumber: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("ТО-$nextNumber") }
    var mileage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое ТО") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(mileage, { mileage = it.filter { c -> c.isDigit() } },
                    label = { Text("Пробег, км") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    dao.addMaintenance(Maintenance(carId = carId, name = name.trim(),
                        mileage = mileage.toIntOrNull() ?: 0))
                    onDismiss()
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ── ЭКРАН 3: ЧТО ДЕЛАЛОСЬ В ТО ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(dao: GarageDao, id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val maintenance by produceState<Maintenance?>(null, id) { value = dao.maintenance(id) }
    val items by dao.items(id).collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(maintenance?.name ?: "ТО") },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            if (items.isEmpty()) Text("Записей пока нет — добавьте первую ниже", color = Color.Gray)

            items.forEach { w ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(w.title, fontWeight = FontWeight.SemiBold)
                        if (w.partNumber.isNotBlank())
                            Text("Артикул: ${w.partNumber}", fontSize = 13.sp, color = Color.Gray)
                        if (w.price > 0) Text("${w.price} руб.", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Итого: ${items.sumOf { it.price }} руб.", fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Что делалось (например, замена масла)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(part, { part = it }, modifier = Modifier.weight(1.4f),
                    label = { Text("Артикул") }, singleLine = true)
                OutlinedTextField(price, { price = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f), label = { Text("Цена") }, singleLine = true)
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                if (title.isBlank()) return@Button
                scope.launch {
                    dao.addItem(WorkItem(maintenanceId = id, title = title.trim(),
                        partNumber = part.trim(), price = price.toIntOrNull() ?: 0))
                    title = ""; part = ""; price = ""
                }
            }) { Text("Добавить запись") }
        }
    }
}
