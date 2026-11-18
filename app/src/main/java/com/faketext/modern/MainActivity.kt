package com.faketext.modern

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class FakeMessageType(
    val label: String,
    val typeCode: Int,
    val targetUri: Uri
) {
    INBOX("Inbox", Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.Inbox.CONTENT_URI),
    OUTBOX("Outbox", Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.Outbox.CONTENT_URI),
    SENT("Sent", Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.Sent.CONTENT_URI),
    FAILED("Failed", Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.CONTENT_URI),
    DRAFT("Draft", Telephony.Sms.MESSAGE_TYPE_DRAFT, Telephony.Sms.Draft.CONTENT_URI)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                FakeTextScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeTextScreen() {
    val context = LocalContext.current

    var senderInput by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FakeMessageType.INBOX) }
    var selectedTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showRestrictedHelpDialog by remember { mutableStateOf(false) }
    var previousDefaultSmsPkg by remember { mutableStateOf<String?>(null) }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val picked = readContactAddressOrName(context, uri)
            if (!picked.isNullOrBlank()) {
                senderInput = picked
            } else {
                Toast.makeText(context, "Could not read contact details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "Contacts permission is needed to pick a contact", Toast.LENGTH_SHORT).show()
        }
    }

    fun performInsert() {
        val inserted = insertFakeSms(
            context = context,
            address = senderInput.trim(),
            body = messageBody.trim(),
            timestampMillis = selectedTimeMillis,
            messageType = selectedType
        )
        if (inserted) {
            showSuccessDialog = true
        } else {
            Toast.makeText(
                context,
                "Could not save message. Make sure this app was allowed as Default SMS.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultSmsApp(context)) {
            performInsert()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                showRestrictedHelpDialog = true
            } else {
                Toast.makeText(
                    context,
                    "Please allow Fake Text as Default SMS app for a moment to save the message.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun onFakeClicked() {
        if (senderInput.isBlank()) {
            Toast.makeText(context, "Please enter a name or phone number", Toast.LENGTH_SHORT).show()
            return
        }
        if (messageBody.isBlank()) {
            Toast.makeText(context, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }

        if (isDefaultSmsApp(context)) {
            performInsert()
        } else {
            val currentDefault = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
            if (!currentDefault.isNullOrBlank() && currentDefault != context.packageName) {
                previousDefaultSmsPkg = currentDefault
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                val roleIntent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                if (roleIntent != null) {
                    roleLauncher.launch(roleIntent)
                } else {
                    Toast.makeText(context, "SMS role is not available on this device", Toast.LENGTH_LONG).show()
                }
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
                roleLauncher.launch(intent)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Fake Text Message",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Sender / Recipient
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sender or Recipient",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = senderInput,
                            onValueChange = { senderInput = it },
                            label = { Text("Phone Number or Name") },
                            placeholder = { Text("e.g. David") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    contactPickerLauncher.launch(null)
                                } else {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Contacts")
                        }
                    }
                }
            }

            // 2. Date & Time
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Date and Time of Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(timeFormatter.format(selectedTimeMillis))
                        }
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dateFormatter.format(selectedTimeMillis))
                        }
                    }
                }
            }

            // 3. Message Type
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Message Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val rows = listOf(
                        listOf(FakeMessageType.INBOX, FakeMessageType.OUTBOX),
                        listOf(FakeMessageType.SENT, FakeMessageType.FAILED),
                        listOf(FakeMessageType.DRAFT)
                    )
                    rows.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedType = item }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedType == item,
                                        onClick = { selectedType = item }
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 4. Message Text
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "This message will NOT be sent to anyone. It is only added locally to your messaging app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        label = { Text("Type your message") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Action Button
            Button(
                onClick = { onFakeClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "FAKE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isDefaultSmsApp(context)) {
                OutlinedButton(
                    onClick = { openSwitchBackOptions(context, previousDefaultSmsPkg) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Switch Back to Normal Messaging App")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val localCal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val utcInitCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH)
            )
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcInitCal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedUtcMillis = datePickerState.selectedDateMillis
                        if (pickedUtcMillis != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtcMillis
                            }
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = selectedTimeMillis
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            selectedTimeMillis = updated.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = Calendar.getInstance().apply {
                            timeInMillis = selectedTimeMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        selectedTimeMillis = updated.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Fake Message Saved!") },
            text = {
                Text(
                    "Your message has been added. Switch back to your normal messaging app now so your regular texts continue working normally."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        openSwitchBackOptions(context, previousDefaultSmsPkg)
                    }
                ) {
                    Text("Switch Back Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Stay Here")
                }
            }
        )
    }

    if (showRestrictedHelpDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedHelpDialog = false },
            title = { Text("One Quick Setting Needed") },
            text = {
                Text(
                    "Newer Android phones block SMS apps that aren't from the Play Store until you unlock them once.\n\n" +
                        "Tap 'Open App Info' below → tap the 3 dots (⋮) in the top right → tap 'Allow restricted settings', then try again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedHelpDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open App Info")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedHelpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun isDefaultSmsApp(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

private fun insertFakeSms(
    context: Context,
    address: String,
    body: String,
    timestampMillis: Long,
    messageType: FakeMessageType
): Boolean {
    return try {
        val threadId = runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.getOrDefault(-1L)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.DATE_SENT, timestampMillis)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, messageType.typeCode)
            if (threadId > 0L) {
                put(Telephony.Sms.THREAD_ID, threadId)
            }
        }
        val uri = context.contentResolver.insert(messageType.targetUri, values)
        uri != null
    } catch (_: Exception) {
        false
    }
}

private fun openSwitchBackOptions(context: Context, previousPkg: String?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !previousPkg.isNullOrBlank()) {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, previousPkg)
        }
        runCatching { context.startActivity(intent) }.onSuccess { return }
    }

    if (!previousPkg.isNullOrBlank()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(previousPkg)
        if (launchIntent != null) {
            runCatching { context.startActivity(launchIntent) }.onSuccess { return }
        }
    }

    val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    runCatching { context.startActivity(defaultAppsIntent) }
}

private fun readContactAddressOrName(context: Context, contactUri: Uri): String? {
    val resolver = context.contentResolver
    resolver.query(
        contactUri,
        arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        ),
        null,
        null,
        null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val id = cursor.getString(0)
        val displayName = cursor.getString(1)
        val hasPhone = cursor.getInt(2) > 0

        if (hasPhone && !id.isNullOrBlank()) {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(id),
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    val number = phoneCursor.getString(0)
                    if (!number.isNullOrBlank()) return number
                }
            }
        }
        return displayName
    }
    return null
}
