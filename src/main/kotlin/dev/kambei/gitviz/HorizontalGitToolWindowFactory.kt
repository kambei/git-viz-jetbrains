package dev.kambei.gitviz

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.awt.*
import javax.swing.*
import kotlin.math.max

class HorizontalGitToolWindowFactory : ToolWindowFactory, DumbAware {
    // Defaults; users can change via the UI button
    private var maxCommitsToShow: Int = 500
    private var maxBranchesToShow: Int = 20

    // Filters
    private var filterBranch: String = ""
    private var filterTag: String = ""
    private var filterAuthor: String = ""
    private var filterMessage: String = ""

    // UI references to update models on repo load
    private var branchCombo: JComboBox<String>? = null
    private var tagCombo: JComboBox<String>? = null
    private var authorCombo: JComboBox<String>? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // Top bar with status (left) and controls (right)
        val statusLabel = JLabel("")
        val topBar = JPanel(BorderLayout())
        topBar.add(statusLabel, BorderLayout.WEST)
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        val limitsBtn = JButton("Limits…")
        limitsBtn.toolTipText = "Choose how many latest commits and branches to show"
        val zoomOutBtn = JButton("-")
        val zoomInBtn = JButton("+")
        zoomOutBtn.toolTipText = "Zoom out"
        zoomInBtn.toolTipText = "Zoom in"
        rightPanel.add(limitsBtn)
        rightPanel.add(zoomOutBtn)
        rightPanel.add(zoomInBtn)
        topBar.add(rightPanel, BorderLayout.EAST)

        // Filter bar
        val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        // Branch select
        val branchBox = JComboBox<String>()
        branchBox.prototypeDisplayValue = "refs/heads/long-branch-name"
        branchBox.toolTipText = "Select a local branch (or empty for all)"
        // Tag select
        val tagBox = JComboBox<String>()
        tagBox.prototypeDisplayValue = "v2025.10.04"
        tagBox.toolTipText = "Select a tag (or empty for all)"
        // Author select
        val authorBox = JComboBox<String>()
        authorBox.prototypeDisplayValue = "Firstname Lastname"
        authorBox.toolTipText = "Select an author (or empty for all)"
        // Commit message substring (free text)
        val messageField = JTextField(18)
        messageField.toolTipText = "Commit message substring (case-insensitive)"
        messageField.text = filterMessage

        // keep references
        branchCombo = branchBox
        tagCombo = tagBox
        authorCombo = authorBox

        // Initialize with current filters
        val applyBtn = JButton("Apply")
        val clearBtn = JButton("Clear")
        filterBar.add(JLabel("Filter:"))
        filterBar.add(JLabel("Branch:"))
        filterBar.add(branchBox)
        filterBar.add(JLabel("Tag:"))
        filterBar.add(tagBox)
        filterBar.add(JLabel("Author:"))
        filterBar.add(authorBox)
        filterBar.add(JLabel("Message:"))
        filterBar.add(messageField)
        filterBar.add(applyBtn)
        filterBar.add(clearBtn)

        val topStack = JPanel(BorderLayout())
        topStack.add(topBar, BorderLayout.NORTH)
        topStack.add(filterBar, BorderLayout.SOUTH)
        panel.add(topStack, BorderLayout.NORTH)

        val commitsPanel = JPanel(BorderLayout())
        val scroll = JBScrollPane(commitsPanel)
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        panel.add(scroll, BorderLayout.CENTER)

        // Wire zoom buttons once a graph is present
        val graphProvider: () -> GraphPanel? = { commitsPanel.getClientProperty("graph") as? GraphPanel }
        zoomInBtn.addActionListener {
            graphProvider()?.zoomIn()
        }
        zoomOutBtn.addActionListener {
            graphProvider()?.zoomOut()
        }

        // Wire filter buttons
        applyBtn.addActionListener {
            val bSel = (branchBox.selectedItem as? String)?.trim().orEmpty()
            val tSel = (tagBox.selectedItem as? String)?.trim().orEmpty()
            val aSel = (authorBox.selectedItem as? String)?.trim().orEmpty()
            filterBranch = bSel
            filterTag = tSel
            filterAuthor = aSel
            filterMessage = messageField.text.trim()
            SwingUtilities.invokeLater {
                loadGitData(project, commitsPanel, statusLabel)
            }
        }
        clearBtn.addActionListener {
            filterBranch = ""
            filterTag = ""
            filterAuthor = ""
            filterMessage = ""
            branchBox.selectedIndex = if (branchBox.itemCount > 0) 0 else -1
            tagBox.selectedIndex = if (tagBox.itemCount > 0) 0 else -1
            authorBox.selectedIndex = if (authorBox.itemCount > 0) 0 else -1
            messageField.text = ""
            SwingUtilities.invokeLater {
                loadGitData(project, commitsPanel, statusLabel)
            }
        }

        // Limits button popup
        limitsBtn.addActionListener { e ->
            val form = JPanel(GridBagLayout())
            val gbc = GridBagConstraints()
            gbc.insets = Insets(4, 4, 4, 4)
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.anchor = GridBagConstraints.WEST
            form.add(JLabel("Latest commits:"), gbc)
            val commitSpinner = JSpinner(SpinnerNumberModel(maxCommitsToShow, 1, 2_000_000, 1))
            gbc.gridx = 1
            form.add(commitSpinner, gbc)
            gbc.gridx = 0
            gbc.gridy = 1
            form.add(JLabel("Latest branches:"), gbc)
            val branchSpinner = JSpinner(SpinnerNumberModel(maxBranchesToShow, 1, 5_000, 1))
            gbc.gridx = 1
            form.add(branchSpinner, gbc)
            val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
            val ok = JButton("OK")
            val cancel = JButton("Cancel")
            buttons.add(ok)
            buttons.add(cancel)
            gbc.gridx = 0
            gbc.gridy = 2
            gbc.gridwidth = 2
            gbc.anchor = GridBagConstraints.EAST
            form.add(buttons, gbc)

            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(form, form)
                .setRequestFocus(true)
                .setResizable(true)
                .setMovable(true)
                .setCancelOnClickOutside(true)
                .createPopup()

            ok.addActionListener {
                maxCommitsToShow = (commitSpinner.value as Number).toInt()
                maxBranchesToShow = (branchSpinner.value as Number).toInt()
                popup.closeOk(null)
                SwingUtilities.invokeLater {
                    loadGitData(project, commitsPanel, statusLabel)
                }
            }
            cancel.addActionListener { popup.cancel() }

            val where = RelativePoint(limitsBtn, Point(0, limitsBtn.height))
            popup.show(where)
        }

        SwingUtilities.invokeLater {
            loadGitData(project, commitsPanel, statusLabel)
        }

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun loadGitData(project: Project, commitsPanel: JPanel, statusLabel: JLabel) {
        val basePath = project.basePath
        if (basePath == null) {
            statusLabel.text = "No project path"
            return
        }
        try {
            val repo = RepositoryBuilder()
                .findGitDir(java.io.File(basePath))
                .build()
            repo.use { repository ->
                val revWalk = RevWalk(repository)
                revWalk.use {
                    // Choose latest N local branches by tip commit time
                    val headRefs = repository.refDatabase.getRefsByPrefix("refs/heads/")
                    val allSortedHeads = headRefs.mapNotNull { r ->
                        val oid = r.objectId ?: r.peeledObjectId
                        if (oid != null) {
                            try {
                                val rc = it.parseCommit(oid)
                                Pair(r, rc.commitTime)
                            } catch (_: Exception) {
                                Pair(r, Int.MIN_VALUE)
                            }
                        } else null
                    }.sortedByDescending { p -> p.second }
                        .map { p -> p.first }
                        .take(maxBranchesToShow)

                    // Populate Branch combo (empty + branch names)
                    runCatching {
                        val names = mutableListOf("")
                        names.addAll(allSortedHeads.map { r -> r.name.removePrefix("refs/heads/") })
                        val model = DefaultComboBoxModel(names.toTypedArray())
                        branchCombo?.model = model
                        if (filterBranch.isNotEmpty()) branchCombo?.selectedItem = filterBranch else branchCombo?.selectedIndex = 0
                    }

                    // Collect tags and populate Tag combo
                    val tagRefs = repository.refDatabase.getRefsByPrefix("refs/tags/")
                    val tagNames = tagRefs.map { it.name.removePrefix("refs/tags/") }.sorted()
                    runCatching {
                        val names = mutableListOf("")
                        names.addAll(tagNames)
                        val model = DefaultComboBoxModel(names.toTypedArray())
                        tagCombo?.model = model
                        if (filterTag.isNotEmpty()) tagCombo?.selectedItem = filterTag else tagCombo?.selectedIndex = 0
                    }

                    // Prepare map commitId -> list of tag names (short)
                    val tagsByCommit = mutableMapOf<String, MutableList<String>>()
                    for (ref in tagRefs) {
                        val peeled = repository.refDatabase.peel(ref)
                        val objId = peeled.peeledObjectId ?: ref.objectId
                        val key = objId?.name()
                        if (key != null) {
                            tagsByCommit.computeIfAbsent(key) { mutableListOf() }
                                .add(ref.name.removePrefix("refs/tags/"))
                        }
                    }

                    // Apply branch filter if provided
                    val branchFilter = filterBranch.trim()
                    val usingBranchFilter = branchFilter.isNotEmpty()
                    val matchingHead: Ref? = if (usingBranchFilter) {
                        headRefs.firstOrNull { r -> r.name.removePrefix("refs/heads/").equals(branchFilter, ignoreCase = true) }
                    } else null
                    val sortedHeads = if (matchingHead != null) listOf(matchingHead) else allSortedHeads

                    val startPoints = LinkedHashSet<ObjectId>()
                    for (r in sortedHeads) {
                        val oid = r.objectId ?: r.peeledObjectId
                        if (oid != null) startPoints.add(oid)
                    }
                    if (startPoints.isEmpty()) {
                        // try resolve specific branch ref if user typed exact full ref
                        if (usingBranchFilter) {
                            val refByName = repository.findRef("refs/heads/$branchFilter")
                            val oid = refByName?.objectId ?: refByName?.peeledObjectId
                            if (oid != null) startPoints.add(oid)
                        }
                        if (startPoints.isEmpty()) {
                            repository.resolve("HEAD")?.let { sp -> startPoints.add(sp) }
                        }
                    }

                    // Populate Author combo by walking a limited number of commits from start points
                    runCatching {
                        RevWalk(repository).use { aw ->
                            aw.sort(org.eclipse.jgit.revwalk.RevSort.TOPO)
                            aw.sort(org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC)
                            for (sp in startPoints) {
                                val rc = aw.parseCommit(sp)
                                aw.markStart(rc)
                            }
                            val authors = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
                            var cc: RevCommit? = aw.next()
                            var count = 0
                            val maxScan = maxCommitsToShow.coerceAtLeast(1000)
                            while (cc != null && count < maxScan) {
                                cc.authorIdent?.name?.let { n -> if (n.isNotBlank()) authors.add(n) }
                                count++
                                cc = aw.next()
                            }
                            val names = mutableListOf("")
                            names.addAll(authors)
                            val model = DefaultComboBoxModel(names.toTypedArray())
                            authorCombo?.model = model
                            if (filterAuthor.isNotEmpty()) authorCombo?.selectedItem = filterAuthor else authorCombo?.selectedIndex = 0
                        }
                    }

                    // Configure RevWalk with topo ordering and time desc
                    it.reset()
                    it.sort(org.eclipse.jgit.revwalk.RevSort.TOPO)
                    it.sort(org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC)
                    for (sp in startPoints) {
                        val rc = it.parseCommit(sp)
                        it.markStart(rc)
                    }
                    val log = mutableListOf<RevCommit>()
                    val authorFilter = filterAuthor.trim()
                    val msgFilter = filterMessage.trim().lowercase()
                    val tagFilter = filterTag.trim()
                    var c: RevCommit? = it.next()
                    while (c != null && log.size < maxCommitsToShow) {
                        var include = true
                        if (authorFilter.isNotEmpty()) {
                            val a = c.authorIdent?.name ?: ""
                            include = include && a.equals(authorFilter, ignoreCase = true)
                        }
                        if (tagFilter.isNotEmpty()) {
                            val tlist = tagsByCommit[c.id.name()] ?: emptyList()
                            include = include && tlist.any { tn -> tn.equals(tagFilter, ignoreCase = true) }
                        }
                        if (msgFilter.isNotEmpty()) {
                            val m = c.fullMessage?.lowercase() ?: c.shortMessage?.lowercase() ?: ""
                            include = include && m.contains(msgFilter)
                        }
                        if (include) log.add(c)
                        c = it.next()
                    }

                    val allowedBranchNames: Set<String> = if (usingBranchFilter && matchingHead != null) {
                        setOf(matchingHead.name.removePrefix("refs/heads/"))
                    } else {
                        allSortedHeads.map { r -> r.name.removePrefix("refs/heads/") }.toSet()
                    }
                    val refsByCommit = collectRefs(repository, allowedBranchNames)
                    val filterNote = buildString {
                        if (usingBranchFilter) append(" • branch=\"$branchFilter\"")
                        if (filterTag.trim().isNotEmpty()) append(" • tag=\"${filterTag.trim()}\"")
                        if (authorFilter.isNotEmpty()) append(" • author=\"$authorFilter\"")
                        if (msgFilter.isNotEmpty()) append(" • message~=\"$msgFilter\"")
                    }
                    statusLabel.text = "Showing ${log.size} commits • ${allowedBranchNames.size} branches$filterNote"
                    val graph = GraphPanel(log.asReversed(), refsByCommit)

                    commitsPanel.removeAll()
                    // Wrap graph in a centering holder so it stays centered when content is smaller than the viewport
                    val holder = JPanel(GridBagLayout())
                    holder.isOpaque = false
                    holder.add(graph, GridBagConstraints())
                    commitsPanel.add(holder, BorderLayout.CENTER)
                    // Expose graph to toolbar via client property
                    commitsPanel.putClientProperty("graph", graph)
                    commitsPanel.revalidate()
                    commitsPanel.repaint()
                    // Center the graph initially in the viewport when content is larger than the viewport
                    SwingUtilities.invokeLater {
                        val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, holder) as? JViewport
                        if (viewport != null) {
                            val ps = holder.preferredSize
                            val ext = viewport.extentSize
                            val cx = ((ps.width - ext.width) / 2).coerceAtLeast(0)
                            val cy = ((ps.height - ext.height) / 2).coerceAtLeast(0)
                            viewport.viewPosition = Point(cx, cy)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            statusLabel.text = "Git repo not found or error: ${e.message}"
        }
    }

    private fun collectRefs(repository: org.eclipse.jgit.lib.Repository, allowedLocalBranches: Set<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        val allRefs = repository.refDatabase.getRefs()
        fun addRef(oid: ObjectId?, name: String) {
            if (oid == null) return
            val key = oid.name()
            result.computeIfAbsent(key) { mutableListOf() }.add(name)
        }
        for (ref in allRefs) {
            val peeled = repository.refDatabase.peel(ref)
            val objId = peeled.peeledObjectId ?: ref.objectId
            when {
                ref.name.startsWith("refs/heads/") -> {
                    val shortName = ref.name.removePrefix("refs/heads/")
                    if (shortName in allowedLocalBranches) {
                        addRef(objId, "Branch $shortName")
                    }
                }
                ref.name.startsWith("refs/tags/") -> {
                    val shortName = ref.name.removePrefix("refs/tags/")
                    addRef(objId, "Tag $shortName")
                }
                // Skip remotes to avoid clutter when limiting branches
                else -> {
                    // ignore other refs
                }
            }
        }
        return result
    }

    private fun createCommitCard(commit: RevCommit, refs: List<String>): JComponent {
        val panel = JPanel()
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xCCCCCC)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        )
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = Color(0xFAFAFA)

        val top = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        top.isOpaque = false
        val sha = commit.name.substring(0, 7)
        top.add(badge(sha, Color(0xE8F0FE), Color(0x1A73E8)))
        val author = commit.authorIdent?.name ?: "Unknown"
        top.add(smallLabel(author))
        panel.add(top)

        val msg = commit.shortMessage ?: ""
        val msgLabel = JLabel(escapeHtml(msg))
        msgLabel.toolTipText = commit.fullMessage
        panel.add(msgLabel)

        if (refs.isNotEmpty()) {
            val refsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
            refsPanel.isOpaque = false
            for (r in refs) {
                refsPanel.add(badge(r, Color(0xE6F4EA), Color(0x137333)))
            }
            panel.add(refsPanel)
        }

        return panel
    }

    private fun smallLabel(text: String): JLabel {
        val l = JLabel(text)
        l.font = l.font.deriveFont(Font.PLAIN, 11f)
        l.foreground = Color(0x555555)
        return l
    }

    private fun badge(text: String, bg: Color, fg: Color): JComponent {
        val l = JLabel(text)
        l.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        l.foreground = fg
        val p = object : JPanel() {
            override fun getInsets(): Insets = Insets(0, 0, 0, 0)
        }
        p.layout = FlowLayout(FlowLayout.CENTER, 0, 0)
        p.background = bg
        p.add(l)
        p.border = BorderFactory.createLineBorder(bg.darker())
        return p
    }

    private fun escapeHtml(s: String): String = s
}

private class GraphPanel(
    commitsNewestFirst: List<RevCommit>,
    private val refsByCommit: Map<String, List<String>>
) : JComponent() {

    private val commits: List<RevCommit> = commitsNewestFirst
    private val indexById: Map<String, Int>
    private val laneByIndex: IntArray
    private val laneCount: Int
    private var specialLane: Int = 0
    private val extraLaneGap: Int get() = rowGap

    private val colGap = 180
    private val rowGap = 52
    // Extra padding so top badges and labels are not cut and to allow some panning room
    private val pad = Insets(96, 64, 96, 64)
    private val nodeRadius = 7

    // Hit regions for interactions (logical coordinates, before scaling)
    private val topLabelRects: Array<Rectangle> = Array(commits.size) { Rectangle() }
    private val msgLabelRects: Array<Rectangle> = Array(commits.size) { Rectangle() }

    // Interaction state
    private var scale = 1.0f
    private val minScale = 0.5f
    private val maxScale = 3.0f

    init {
        val idx = HashMap<String, Int>(commits.size)
        for ((i, c) in commits.withIndex()) idx[c.id.name()] = i
        indexById = idx

        // Stable branch-based lane assignment: commits belonging to the same branch stay on the same lane and color
        // 1) Seed branch names from refs (labels starting with "Branch ") at tip commits
        val branchNameByIndex = arrayOfNulls<String>(commits.size)
        for (i in commits.indices) {
            val id = commits[i].id.name()
            val labels = refsByCommit[id] ?: emptyList()
            val branchLabels = labels.filter { it.startsWith("Branch ") }
            if (branchLabels.isNotEmpty()) {
                val chosen = branchLabels.map { it.removePrefix("Branch ").trim() }.sorted().first()
                if (branchNameByIndex[i] == null) branchNameByIndex[i] = chosen
            }
        }

        fun firstParentIndexOf(idx: Int): Int? {
            val parents = commits[idx].parents ?: emptyArray()
            if (parents.isEmpty()) return null
            return indexById[parents[0].id.name()]
        }

        // 2) Propagate branch names backwards along first-parent chains from each seeded tip
        for (i in commits.indices.reversed()) {
            val b = branchNameByIndex[i] ?: continue
            var p = firstParentIndexOf(i)
            while (p != null && branchNameByIndex[p] == null) {
                branchNameByIndex[p] = b
                p = firstParentIndexOf(p)
            }
        }

        // 3) Build first-parent children map to allow inheriting branch from children where missing
        val firstParentChildren = Array(commits.size) { mutableListOf<Int>() }
        for (i in commits.indices) {
            val p = firstParentIndexOf(i)
            if (p != null) firstParentChildren[p].add(i)
        }
        for (i in commits.indices.reversed()) {
            if (branchNameByIndex[i] == null) {
                val fromChild = firstParentChildren[i].asSequence()
                    .map { child -> branchNameByIndex[child] }
                    .filterNotNull()
                    .firstOrNull()
                if (fromChild != null) branchNameByIndex[i] = fromChild
            }
        }

        // 4) Fallback for any still-unassigned commits: put them into a common "root" bucket
        for (i in commits.indices) {
            if (branchNameByIndex[i] == null) branchNameByIndex[i] = "root"
        }

        // 5) Assign stable lane indices per branch based on first appearance order
        val laneOfBranch = LinkedHashMap<String, Int>()
        for (i in commits.indices) {
            val b = branchNameByIndex[i]!!
            if (!laneOfBranch.containsKey(b)) laneOfBranch[b] = laneOfBranch.size
        }
        val lanes = IntArray(commits.size) { idx -> laneOfBranch[branchNameByIndex[idx]!!] ?: 0 }
        laneByIndex = lanes
        laneCount = laneOfBranch.size

        // Special handling: visually separate the checked-out branch (HEAD) vertically
        // With oldest→newest ordering, the newest commit is at the last index
        val headLane = if (commits.isNotEmpty()) lanes[commits.lastIndex] else 0
        this.specialLane = headLane

        toolTipText = "" // enable dynamic tooltips

        // Mouse wheel zoom (Ctrl+wheel) and drag-to-pan
        addMouseWheelListener { e ->
            if (e.isControlDown) {
                val oldScale = scale
                val factor = if (e.wheelRotation < 0) 1.1f else 0.9f
                val newScale = (scale * factor).coerceIn(minScale, maxScale)
                if (newScale != scale) {
                    val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport
                    val viewPos = viewport?.viewPosition ?: Point(0, 0)
                    val mouse = e.point
                    // Anchor in logical coords before scaling
                    val anchorX = (viewPos.x + mouse.x) / oldScale
                    val anchorY = (viewPos.y + mouse.y) / oldScale
                    scale = newScale
                    revalidate()
                    // Keep anchor under the mouse after scaling
                    val newViewX = (anchorX * newScale - mouse.x).toInt().coerceAtLeast(0)
                    val newViewY = (anchorY * newScale - mouse.y).toInt().coerceAtLeast(0)
                    viewport?.viewPosition = Point(newViewX, newViewY)
                    repaint()
                }
                e.consume()
            }
        }

        val self = this
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 1) {
                    // Priority: author label -> message label -> node
                    val authorIdx = findAuthorAt(e.point)
                    if (authorIdx != null) {
                        val c = commits[authorIdx]
                        val name = c.authorIdent?.name ?: "Unknown"
                        val email = c.authorIdent?.emailAddress ?: ""
                        val label = JLabel(if (email.isNotBlank()) "$name <$email>" else name)
                        label.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
                        val popup = JBPopupFactory.getInstance()
                            .createComponentPopupBuilder(label, null)
                            .setTitle("Author")
                            .setMovable(false)
                            .setResizable(false)
                            .setRequestFocus(false)
                            .createPopup()
                        popup.show(RelativePoint(self, e.point))
                        return
                    }

                    val msgIdx = findMessageAt(e.point)
                    if (msgIdx != null) {
                        val c = commits[msgIdx]
                        showFullMessagePopup(c, e.point)
                        return
                    }

                    val idx = findCommitAt(e.point)
                    if (idx != null) {
                        val c = commits[idx]
                        showFullMessagePopup(c, e.point)
                    }
                }
            }
        })

    }

    private fun findAuthorAt(screenPoint: Point): Int? {
        val lx = (screenPoint.x / scale).toInt()
        val ly = (screenPoint.y / scale).toInt()
        for (i in commits.indices) {
            if (topLabelRects[i].contains(lx, ly)) return i
        }
        return null
    }

    private fun findMessageAt(screenPoint: Point): Int? {
        val lx = (screenPoint.x / scale).toInt()
        val ly = (screenPoint.y / scale).toInt()
        for (i in commits.indices) {
            if (msgLabelRects[i].contains(lx, ly)) return i
        }
        return null
    }

    private fun showFullMessagePopup(c: RevCommit, atPoint: Point) {
        val textArea = JTextArea(c.fullMessage ?: c.shortMessage ?: "")
        textArea.isEditable = false
        textArea.wrapStyleWord = true
        textArea.lineWrap = true
        textArea.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val scroll = JBScrollPane(textArea)
        scroll.preferredSize = Dimension(420, 240)
        val authorName = c.authorIdent?.name ?: ""
        val title = "${c.name.substring(0,7)} · $authorName"
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scroll, null)
            .setTitle(title)
            .setMovable(false)
            .setResizable(true)
            .setRequestFocus(true)
            .createPopup()
        popup.show(RelativePoint(this, atPoint))
    }

    fun zoomIn() { zoomBy(1.1f) }

    fun zoomOut() { zoomBy(1f / 1.1f) }

    private fun zoomBy(factor: Float) {
        val oldScale = scale
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        if (newScale == scale) return
        val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport
        val viewPos = viewport?.viewPosition ?: Point(0, 0)
        val center = if (viewport != null) Point(viewport.width / 2, viewport.height / 2) else Point(width / 2, height / 2)
        val anchorX = (viewPos.x + center.x) / oldScale
        val anchorY = (viewPos.y + center.y) / oldScale
        scale = newScale
        revalidate()
        if (viewport != null) {
            val newViewX = (anchorX * newScale - center.x).toInt().coerceAtLeast(0)
            val newViewY = (anchorY * newScale - center.y).toInt().coerceAtLeast(0)
            viewport.viewPosition = Point(newViewX, newViewY)
        }
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val baseW = pad.left + pad.right + max(1, commits.size) * colGap
        val extra = if (laneCount - 1 > specialLane) extraLaneGap else 0
        val baseH = pad.top + pad.bottom + max(1, laneCount) * rowGap + extra
        return Dimension((baseW * scale).toInt(), (baseH * scale).toInt())
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.scale(scale.toDouble(), scale.toDouble())

        // Draw edges first
        for (i in commits.indices) {
            val c = commits[i]
            val x1 = xForIndex(i)
            val y1 = yForLane(laneByIndex[i])
            val parents = c.parents ?: emptyArray()
            for ((k, p) in parents.withIndex()) {
                val j = indexById[p.id.name()] ?: continue
                val x2 = xForIndex(j)
                val y2 = yForLane(laneByIndex[j])
                val y1Adj = if (parents.size > 1) {
                    val offset = ((k - (parents.size - 1) / 2.0f) * 6f).toInt()
                    y1 + offset
                } else y1
                val color = if (k == 0) colorForLane(laneByIndex[i]) else colorForLane(laneByIndex[j])
                drawCurve(g2, x1, y1Adj, x2, y2, color)
            }
        }

        // Draw nodes and labels over edges
        for (i in commits.indices) {
            val c = commits[i]
            val x = xForIndex(i)
            val y = yForLane(laneByIndex[i])

            // Node
            val nodeCol = colorForLane(laneByIndex[i])
            g2.color = nodeCol
            g2.fillOval(x - nodeRadius, y - nodeRadius, nodeRadius * 2, nodeRadius * 2)
            g2.color = JBColor.WHITE
            g2.drawOval(x - nodeRadius, y - nodeRadius, nodeRadius * 2, nodeRadius * 2)

            // SHA + author above (width-constrained)
            val sha = c.name.substring(0, 7)
            val author = c.authorIdent?.name ?: ""
            val topLabel = if (author.isNotBlank()) "$sha  ·  $author" else sha
            val fm = getFontMetrics(g2.font)
            val maxW = colGap - 24
            g2.color = JBColor(Color(0x222222), Color(0xFFFFFF))
            var topFit = topLabel
            if (fm.stringWidth(topFit) > maxW) {
                val ell = "…"
                var lo = 0
                var hi = topFit.length
                var best = ell
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val cand = topLabel.substring(0, mid) + ell
                    if (fm.stringWidth(cand) <= maxW) {
                        best = cand
                        lo = mid + 1
                    } else hi = mid - 1
                }
                topFit = best
            }
            val topW = fm.stringWidth(topFit)
            val topH = fm.height
            val topBaseY = y - nodeRadius - 6
            val topX = x - topW / 2
            // store hit rect for author/SHA label (whole label area)
            topLabelRects[i].setBounds(topX, topBaseY - fm.ascent, topW, topH)
            g2.drawString(topFit, topX, topBaseY)

            // Message below (width-constrained)
            val msg = c.shortMessage ?: ""
            var msgFit = msg
            if (fm.stringWidth(msgFit) > maxW) {
                val ell = "…"
                var lo = 0
                var hi = msgFit.length
                var best = ell
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val cand = msg.substring(0, mid) + ell
                    if (fm.stringWidth(cand) <= maxW) {
                        best = cand
                        lo = mid + 1
                    } else hi = mid - 1
                }
                msgFit = best
            }
            g2.color = JBColor(Color(0x444444), Color(0xDDDDDD))
            val msgW = fm.stringWidth(msgFit)
            val msgH = fm.height
            val msgBaseY = y + nodeRadius + fm.ascent + 2
            val msgX = x - msgW / 2
            // store hit rect for message label
            msgLabelRects[i].setBounds(msgX, msgBaseY - fm.ascent, msgW, msgH)
            g2.drawString(msgFit, msgX, msgBaseY)

            // Simple ref badges stacked above the top label if present
            val refs = refsByCommit[c.id.name()] ?: emptyList()
            if (refs.isNotEmpty()) {
                // place first badge just above the top label with a small gap
                val gap = 6
                val startBaseline = (topBaseY - fm.ascent - gap)
                var ry = startBaseline
                for (r in refs.take(3)) {
                    drawBadge(g2, r, x, ry)
                    ry -= (fm.height + 6)
                }
            }
        }
    }

    private fun drawCurve(g2: Graphics2D, x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
        val prevColor = g2.color
        val prevStroke = g2.stroke
        g2.color = color
        g2.stroke = BasicStroke(2f)
        val dx = (x2 - x1).coerceAtLeast(10)
        val cx1 = x1 + dx / 2
        val cx2 = x2 - dx / 2
        val curve = java.awt.geom.CubicCurve2D.Float(
            x1.toFloat(), y1.toFloat(),
            cx1.toFloat(), y1.toFloat(),
            cx2.toFloat(), y2.toFloat(),
            x2.toFloat(), y2.toFloat()
        )
        g2.draw(curve)
        g2.color = prevColor
        g2.stroke = prevStroke
    }

    private fun drawBadge(g2: Graphics2D, text: String, centerX: Int, baselineY: Int) {
        val fm = getFontMetrics(g2.font)
        val paddingH = 6
        val paddingV = 2
        val maxW = colGap - 24
        var display = text
        if (fm.stringWidth(display) > maxW) {
            val ell = "…"
            var lo = 0
            var hi = display.length
            var best = ell
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val cand = text.substring(0, mid) + ell
                if (fm.stringWidth(cand) <= maxW) {
                    best = cand
                    lo = mid + 1
                } else hi = mid - 1
            }
            display = best
        }
        val w = fm.stringWidth(display) + paddingH * 2
        val h = fm.ascent + paddingV * 2
        val x = centerX - w / 2
        val y = baselineY - h + fm.ascent
        val bg = JBColor(Color(0xE6F4EA), Color(0x244E2A))
        val fg = JBColor(Color(0x137333), Color(0xA5D6A7))
        val border = JBColor(Color(0xA0CFA8), Color(0x356A3D))
        g2.color = bg
        g2.fillRoundRect(x, y - fm.ascent, w, h, 10, 10)
        g2.color = border
        g2.drawRoundRect(x, y - fm.ascent, w, h, 10, 10)
        g2.color = fg
        g2.drawString(display, x + paddingH, baselineY)
    }

    private fun colorForLane(lane: Int): Color {
        val light = arrayOf(
            Color(0x1A73E8), Color(0x34A853), Color(0xFBBC05), Color(0xE91E63),
            Color(0x00ACC1), Color(0x8E24AA), Color(0xF4511E), Color(0x7CB342)
        )
        val dark = arrayOf(
            Color(0x64B5F6), Color(0x66BB6A), Color(0xFFD54F), Color(0xF48FB1),
            Color(0x26C6DA), Color(0xBA68C8), Color(0xFF8A65), Color(0x9CCC65)
        )
        val idx = if (lane >= 0) lane % light.size else 0
        return JBColor(light[idx], dark[idx])
    }

    private fun yForLane(lane: Int): Int {
        val base = pad.top + lane * rowGap
        return if (lane > specialLane) base + extraLaneGap else base
    }

    // Map commit index to X so time flows left→right with oldest on the left and newest on the right
    private fun xForIndex(index: Int): Int {
        return pad.left + index * colGap
    }

    private fun findCommitAt(screenPoint: Point): Int? {
        val lx = (screenPoint.x / scale).toInt()
        val ly = (screenPoint.y / scale).toInt()
        val rr = (nodeRadius + 4)
        val r2 = rr * rr
        for (i in commits.indices) {
            val x = xForIndex(i)
            val y = yForLane(laneByIndex[i])
            val dx = lx - x
            val dy = ly - y
            if (dx * dx + dy * dy <= r2) return i
        }
        return null
    }

    override fun getToolTipText(event: java.awt.event.MouseEvent): String? {
        val idx = findCommitAt(event.point) ?: return null
        val c = commits[idx]
        val author = c.authorIdent?.name ?: ""
        return "${c.name.substring(0, 7)} $author: ${c.shortMessage}"
    }
}
