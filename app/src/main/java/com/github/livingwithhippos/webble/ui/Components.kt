package com.github.livingwithhippos.webble.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.livingwithhippos.webble.ui.theme.Accents
import com.github.livingwithhippos.webble.ui.theme.Coral
import com.github.livingwithhippos.webble.ui.theme.Ink

/** Big condensed uppercase heading. */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    large: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        style = if (large) {
            MaterialTheme.typography.displaySmall
        } else {
            MaterialTheme.typography.headlineMedium
        },
        color = color,
        modifier = modifier,
    )
}

/** Small rounded label, for statuses and counts. */
@Composable
fun StatPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Filled circular icon button. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** Pill-shaped button with an uppercase label and an optional trailing icon. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Coral,
    content: Color = Color.White,
    @DrawableRes trailingIcon: Int? = null,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(start = 16.dp, end = if (trailingIcon != null) 12.dp else 16.dp),
        modifier = modifier,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Large rounded block of flat colour. */
@Composable
fun ColorBlockCard(
    color: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Ink,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/** Circle with the first letter of [host], coloured from the host so it stays stable. */
@Composable
fun LetterAvatar(host: String, modifier: Modifier = Modifier) {
    val letter = host.removePrefix("www.").firstOrNull()?.uppercaseChar() ?: '•'
    val color = Accents[Math.floorMod(host.hashCode(), Accents.size)]
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
    ) {
        Text(letter.toString(), style = MaterialTheme.typography.headlineSmall, color = Ink)
    }
}

/** Switch colours matching the accent palette. */
@Composable
fun webbleSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Coral,
    checkedBorderColor = Coral,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = Color.Transparent,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)
