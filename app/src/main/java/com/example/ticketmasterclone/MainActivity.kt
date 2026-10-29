package com.example.ticketmasterclone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- DATA MODELS ---
data class TicketmasterResponse(val _embedded: EmbeddedEvents?)
data class EmbeddedEvents(val events: List<ApiEvent>?)
data class ApiEvent(
    val id: String,
    val name: String,
    val dates: EventDates?,
    val _embedded: EventEmbeddedVenues?,
    val priceRanges: List<PriceRange>?
)
data class EventDates(val start: StartDate?)
data class StartDate(val localDate: String?)
data class EventEmbeddedVenues(val venues: List<ApiVenue>?)
data class ApiVenue(val name: String?)
data class PriceRange(val min: Double?)

data class Event(
    val id: String,
    val title: String,
    val venue: String,
    val date: String,
    val category: String,
    val minPrice: Double,
    val accentColor: Color
)

data class Ticket(
    val eventName: String,
    val venue: String,
    val date: String,
    val section: String,
    val row: String,
    val seat: String,
    val price: Double,
    val barcodeId: String
)

// --- RETROFIT SERVICE ---
interface TicketmasterApiService {
    @GET("discovery/v2/events.json")
    suspend fun searchEvents(
        @Query("apikey") apiKey: String,
        @Query("keyword") keyword: String? = null,
        @Query("classificationName") category: String? = null
    ): TicketmasterResponse

    companion object {
        private const val BASE_URL = "https://app.ticketmaster.com/"
        fun create(): TicketmasterApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TicketmasterApiService::class.java)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TicketmasterTheme {
                TicketmasterApp()
            }
        }
    }
}

@Composable
fun TicketmasterTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF026CDF),
        secondary = Color(0xFF1F2633),
        background = Color(0xFF0D1117),
        surface = Color(0xFF161B22),
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketmasterApp() {
    val navController = rememberNavController()
    val myTickets = remember { mutableStateListOf<Ticket>() }
    var selectedEventForCheckout by remember { mutableStateOf<Event?>(null) }

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(onEventSelected = { event -> selectedEventForCheckout = event })
            }
            composable("tickets") {
                MyTicketsScreen(tickets = myTickets)
            }
            composable("profile") {
                ProfileScreen()
            }
        }

        selectedEventForCheckout?.let { event ->
            CheckoutBottomSheet(
                event = event,
                onDismiss = { selectedEventForCheckout = null },
                onPaymentComplete = { newTicket ->
                    myTickets.add(newTicket)
                    selectedEventForCheckout = null
                    navController.navigate("tickets")
                }
            )
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = Color(0xFF161B22)) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") },
            icon = { Icon(Icons.Default.Search, contentDescription = "Discover") },
            label = { Text("Discover") }
        )
        NavigationBarItem(
            selected = currentRoute == "tickets",
            onClick = { navController.navigate("tickets") },
            icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = "My Tickets") },
            label = { Text("My Tickets") }
        )
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { navController.navigate("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
            label = { Text("Account") }
        )
    }
}

@Composable
fun HomeScreen(onEventSelected: (Event) -> Unit) {
    val apiKey = "7sS0B9ojZIkjRiqgbK6sMF5DPL5CzPFl"
    val apiService = remember { TicketmasterApiService.create() }

    var eventList by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("All", "Music", "Sports", "Arts & Theatre")
    var selectedCategory by remember { mutableStateOf("All") }

    LaunchedEffect(searchQuery, selectedCategory) {
        isLoading = true
        try {
            val catQuery = if (selectedCategory == "All") null else selectedCategory
            val response = apiService.searchEvents(
                apiKey = apiKey,
                keyword = searchQuery.ifEmpty { null },
                category = catQuery
            )
            val fetchedEvents = response._embedded?.events?.map { apiEvent ->
                Event(
                    id = apiEvent.id,
                    title = apiEvent.name,
                    venue = apiEvent._embedded?.venues?.firstOrNull()?.name ?: "Live Venue",
                    date = apiEvent.dates?.start?.localDate ?: "TBA",
                    category = selectedCategory,
                    minPrice = apiEvent.priceRanges?.firstOrNull()?.min ?: 65.0,
                    accentColor = Color(0xFF026CDF)
                )
            } ?: emptyList()
            eventList = fetchedEvents
        } catch (e: Exception) {
            eventList = emptyList()
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "ticketmaster", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF026CDF))
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search live events & artists") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF026CDF))
            }
        } else if (eventList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No live events found. Check your search or API quota.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(eventList) { event ->
                    EventCard(event = event, onClick = { onEventSelected(event) })
                }
            }
        }
    }
}

@Composable
fun EventCard(event: Event, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(event.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(text = "${event.venue} • ${event.date}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                Text(text = "From $${event.minPrice.toInt()}", color = Color(0xFF026CDF), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF026CDF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Tickets")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutBottomSheet(event: Event, onDismiss: () -> Unit, onPaymentComplete: (Ticket) -> Unit) {
    val context = LocalContext.current
    var selectedTier by remember { mutableStateOf("General Admission") }
    val seatTiers = listOf(
        "VIP Floor" to event.minPrice * 2.5,
        "Lower Bowl" to event.minPrice * 1.5,
        "General Admission" to event.minPrice
    )
    val selectedPrice = seatTiers.first { it.first == selectedTier }.second

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF161B22)) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text(text = "Select Seats", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = event.title, color = Color.Gray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(16.dp))

            seatTiers.forEach { (tier, price) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTier == tier) Color(0xFF026CDF).copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedTier = tier }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tier, fontWeight = FontWeight.Medium)
                    Text("$${price.toInt()}", fontWeight = FontWeight.Bold)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray.copy(alpha = 0.3f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1x Ticket ($selectedTier)")
                Text("$${selectedPrice.toInt()}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Service Fee", color = Color.Gray)
                Text("$15", color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("$${(selectedPrice + 15).toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF00E676))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val barcode = "TM-${(100000..999999).random()}"
                    val mockTicket = Ticket(
                        eventName = event.title,
                        venue = event.venue,
                        date = event.date,
                        section = when (selectedTier) { "VIP Floor" -> "SEC A"; "Lower Bowl" -> "SEC 104"; else -> "GA 12" },
                        row = "14",
                        seat = (10..99).random().toString(),
                        price = selectedPrice + 15,
                        barcodeId = barcode
                    )

                    // Trigger email confirmation receipt via email client intent
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_SUBJECT, "Ticketmaster Order Confirmation: ${event.title}")
                        putExtra(Intent.EXTRA_TEXT, """
                            Thank you for your order!
                            
                            Event: ${event.title}
                            Venue: ${event.venue}
                            Date: ${event.date}
                            Tier: $selectedTier
                            Total Paid: $${(selectedPrice + 15).toInt()}
                            Barcode: $barcode
                            
                            Your tickets are ready in your Ticketmaster App digital wallet.
                        """.trimIndent())
                    }
                    try {
                        context.startActivity(emailIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Purchase Successful! Ticket added to Wallet.", Toast.LENGTH_LONG).show()
                    }

                    onPaymentComplete(mockTicket)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Complete Mock Purchase", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MyTicketsScreen(tickets: List<Ticket>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Tickets", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No upcoming tickets. Purchase one to view!", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(tickets) { ticket -> DigitalTicketCard(ticket = ticket) }
            }
        }
    }
}

@Composable
fun DigitalTicketCard(ticket: Ticket) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2633))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(ticket.eventName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(ticket.venue, color = Color.LightGray, fontSize = 14.sp)
            Text(ticket.date, color = Color(0xFF026CDF), fontWeight = FontWeight.Medium, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SeatInfoItem("SECTION", ticket.section)
                SeatInfoItem("ROW", ticket.row)
                SeatInfoItem("SEAT", ticket.seat)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(36.dp))
                    Text(ticket.barcodeId, color = Color.Black, fontWeight = FontWeight.Monospace, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Transfer") }
                OutlinedButton(onClick = { }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Sell") }
            }
        }
    }
}

@Composable
fun SeatInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF026CDF))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Demo User", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("demo.user@example.com", color = Color.Gray)
    }
}
