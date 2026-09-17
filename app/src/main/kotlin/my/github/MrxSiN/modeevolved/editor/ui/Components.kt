package my.github.MrxSiN.modeevolved.editor.ui

import android.graphics.Matrix
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

import my.github.MrxSiN.modeevolved.R

/** A titled run of rows drawn as one rounded group, the way Settings draws its categories. */
@Composable
fun Section(title: String?, content: @Composable SectionScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        SectionScope.content()
    }
}

/** Where a row sits in its group, which decides its resting corners. */
object SectionScope {

    fun positionAt(index: Int, count: Int): RowPosition = RowPosition(isFirst = index == 0, isLast = index == count - 1)
}

data class RowPosition(val isFirst: Boolean, val isLast: Boolean)

/**
 * One row of a [Section].
 *
 * Expressive rows answer touch with their shape: inner corners round out while
 * pressed and stay round while selected, on the motion scheme's fast spring.
 *
 * @param selected shows a radio button when not null.
 * @param leading drawn in place of [icon], for images that are not resources.
 */
@Composable
fun SectionRow(
    title: String,
    position: RowPosition,
    onClick: () -> Unit,
    supporting: String? = null,
    icon: Int? = null,
    selected: Boolean? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val emphasised = pressed || selected == true
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme

    val top by animateDpAsState(if (position.isFirst || emphasised) OUTER else INNER, motion.fastSpatialSpec())
    val bottom by animateDpAsState(if (position.isLast || emphasised) OUTER else INNER, motion.fastSpatialSpec())
    val container by animateColorAsState(if (selected == true) colors.secondaryContainer else colors.surfaceBright, motion.defaultEffectsSpec())
    val content by animateColorAsState(if (selected == true) colors.onSecondaryContainer else colors.onSurface, motion.defaultEffectsSpec())

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                leading != null -> leading()
                icon != null -> IconBadge(icon, selected = emphasised)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (selected != null) RadioButton(selected = selected, onClick = null)
        }
    }
}

/** A large choice card whose shape, fill and outline animate as it is pressed and chosen. */
@Composable
fun ChoiceCard(title: String, detail: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme

    val corner by animateDpAsState(
        when {
            pressed -> OUTER / 2
            selected -> SELECTED
            else -> OUTER
        },
        motion.fastSpatialSpec(),
    )
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceBright, motion.defaultEffectsSpec())
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, motion.defaultEffectsSpec())
    val outline by animateDpAsState(if (selected) 2.dp else 0.dp, motion.fastEffectsSpec())

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        border = BorderStroke(outline, colors.primary.copy(alpha = if (outline > 0.dp) 1f else 0f)),
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(icon, selected = selected || pressed, large = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/**
 * An icon on a tonal badge that morphs from a circle into a nine-sided cookie
 * when [selected], the shape family Settings uses for Mode icons.
 */
@Composable
fun IconBadge(icon: Int, selected: Boolean = false, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val container by animateColorAsState(
        if (selected) colors.primary else colors.secondaryContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val tint by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSecondaryContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

    Surface(
        shape = MorphShape(morph, progress),
        color = container,
        contentColor = tint,
        modifier = Modifier.size(if (large) 56.dp else 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (large) 28.dp else 24.dp))
        }
    }
}

/**
 * A pill-shaped search bar in the Material 3 Expressive style Settings uses:
 * a tonal container without an outline, a search icon, and a clear button once
 * something is typed.
 */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHighest,
            unfocusedContainerColor = colors.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** One choice among a few, as a Material 3 Expressive connected button group. */
@Composable
fun <T> ConnectedChoice(choices: List<T>, selected: T, onChoose: (T) -> Unit, label: @Composable (T) -> String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        choices.forEachIndexed { index, choice ->
            ToggleButton(
                checked = selected == choice,
                onCheckedChange = { onChoose(choice) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = FilledTonalToggleButtonDefaults.colors(),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(label(choice), maxLines = 1)
            }
        }
    }
}

/** A short explanation on a tonal card. */
@Composable
fun Note(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Body text between sections, such as an intro or an empty-list message. */
@Composable
fun Intro(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** A [Morph] between two unit-sized shapes at [progress], scaled to whatever it outlines. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

private val OUTER: Dp = 28.dp
private val INNER: Dp = 4.dp
private val SELECTED: Dp = 40.dp
private val GAP: Dp = 2.dp
