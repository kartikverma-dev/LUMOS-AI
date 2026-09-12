package com.lumos.ai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumos.ai.data.db.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.ui.collectAsState()
    val listState = rememberLazyListState()

    // True only if the last message is currently the last *visible* item, i.e.
    // the user hasn't scrolled away from the bottom. Read BEFORE we scroll so
    // a manual scroll-up is respected and never yanked back.
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val lastIndex = state.messages.size - 1
            lastIndex < 0 || lastVisible >= lastIndex
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty() && isAtBottom) {
            // scrollToItem (not animateScrollToItem): per-token calls fire many times
            // a second while streaming, and stacking animations on top of each other
            // is what was blocking manual scroll gestures. A new message arriving
            // (size change) still gets a smooth animated scroll below.
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.serverOnline) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                state.serverDetail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.generating) {
                        IconButton(onClick = vm::stop) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Server / Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                state.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message LUMOS…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val canSend = state.input.isNotBlank() && !state.generating
                    IconButton(
                        onClick = vm::send,
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg = msg,
                    isLastAssistant = msg.role == "assistant" &&
                        state.messages.lastOrNull()?.id == msg.id &&
                        !state.generating && msg.content.isNotBlank(),
                    onRegenerate = vm::regenerate,
                    onDelete = vm::deleteMessage
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: MessageEntity,
    isLastAssistant: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isUser = msg.role == "user"
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box {
            Card(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    // Long-press any message (user or assistant) for a quick
                    // Copy / Delete menu. Regenerate stays as the dedicated
                    // button below the last assistant reply.
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true }
                    )
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (msg.role == "assistant" && msg.content.isBlank()) {
                        CircularProgressIndicator(
                            Modifier
                                .size(16.dp)
                                .padding(top = 2.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            msg.content,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.content))
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDelete(msg.id)
                        menuExpanded = false
                    }
                )
            }
        }
        if (isLastAssistant) {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(msg.content)) }) {
                    Text("Copy")
                }
                TextButton(onClick = onRegenerate) { Text("Regenerate") }
            }
        }
    }
}
