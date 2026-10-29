package dev.buildwithtim.minimalui.e2e

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** One node of a uiautomator dump. */
private class Node(
    val text: String,
    val packageName: String,
    val clickable: Boolean,
    val focused: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val children: List<Node>,
) {
    val tree: Sequence<Node> get() = sequenceOf(this) + children.asSequence().flatMap { it.tree }

    // A merged row carries its label itself; otherwise the label is its first text below it.
    val label: String? get() = tree.map { it.text }.firstOrNull { it.isNotBlank() }
}

/** A row of a list on screen: its label, and whether it holds focus, so Enter opens it. */
data class Row(
    val label: String,
    val focused: Boolean,
)

/** What a uiautomator dump of the screen showed. */
class Screen private constructor(
    roots: List<Node>,
    /** The dump itself, for a failure message. */
    val xml: String,
) {
    private val nodes = roots.asSequence().flatMap { it.tree }.toList()
    private val width = nodes.maxOfOrNull { it.right } ?: 0

    /** The app whose window the dump shows. */
    val packageName: String? = nodes.firstOrNull()?.packageName

    /** Every text on screen. */
    val texts: List<String> = nodes.map { it.text }.filter { it.isNotBlank() }

    /**
     * The rows of the list on screen, top to bottom: clickable, and wider than half the screen, as every list row spans
     * the list. The hint row's pills, clickable too, hug their text. A blank row has no label and is left out.
     */
    val rows: List<Row> =
        nodes
            .filter { it.clickable && (it.right - it.left) * 2 > width }
            .sortedBy { it.top }
            .mapNotNull { node -> node.label?.let { Row(it, node.tree.any(Node::focused)) } }

    /** The labels of [rows], top to bottom. */
    val labels: List<String> get() = rows.map { it.label }

    override fun toString(): String = "$packageName rows=$labels texts=$texts"

    companion object {
        private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        /** The screen a `uiautomator dump` file holds. */
        fun parse(xml: String): Screen {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                }
            val hierarchy = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
            return Screen(hierarchy.nodeChildren().map(::node), xml)
        }

        private fun node(element: Element): Node {
            val bounds =
                checkNotNull(BOUNDS.matchEntire(element.getAttribute("bounds"))) { "bounds of $element" }
                    .groupValues
                    .drop(1)
                    .map(String::toInt)
            return Node(
                text = element.getAttribute("text"),
                packageName = element.getAttribute("package"),
                clickable = element.getAttribute("clickable") == "true",
                focused = element.getAttribute("focused") == "true",
                left = bounds[0],
                top = bounds[1],
                right = bounds[2],
                children = element.nodeChildren().map(::node),
            )
        }

        private fun Element.nodeChildren(): List<Element> =
            (0 until childNodes.length)
                .map { childNodes.item(it) }
                .filterIsInstance<Element>()
                .filter { it.tagName == "node" }
    }
}
