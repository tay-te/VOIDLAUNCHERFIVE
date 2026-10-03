package dev.voidmc.hearth.plan.model;

import dev.voidmc.hearth.plan.road.Polyline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The settlement's road network: nodes (the heart, junctions, dead ends, gates) and the edges between them, in the
 * order they were planned. Ids are list indices, so iteration order — and every choice made from it — is stable.
 */
public final class RoadGraph {
	private final List<Node> nodes = new ArrayList<>();
	private final List<RoadEdge> edges = new ArrayList<>();

	public List<Node> nodes() {
		return Collections.unmodifiableList(this.nodes);
	}

	public List<RoadEdge> edges() {
		return Collections.unmodifiableList(this.edges);
	}

	public Node node(int id) {
		return this.nodes.get(id);
	}

	public RoadEdge edge(int id) {
		return this.edges.get(id);
	}

	public Node addNode(int x, int y, int z, NodeKind kind) {
		Node n = new Node(this.nodes.size(), x, y, z, kind);
		this.nodes.add(n);
		return n;
	}

	public RoadEdge addEdge(int from, int to, RoadEdge.Role role, RoadKind kind, int width, Polyline line, int[] runs) {
		RoadEdge e = new RoadEdge(this.edges.size(), from, to, role, kind, width, line, runs, 0, new int[0]);
		this.edges.add(e);
		return e;
	}

	/** Gives an edge a new role (a lane that came out as a stair street); returns the edge that now stands for it. */
	public RoadEdge replaceRole(int id, RoadEdge.Role role) {
		RoadEdge e = this.edges.get(id);
		RoadEdge n = new RoadEdge(e.id, e.from, e.to, role, e.kind, e.width, e.line(), e.runs(), e.built, e.junctions());
		n.parent = e.parent;
		n.parentIndex = e.parentIndex;
		n.limit = e.limit;
		this.edges.set(id, n);
		return n;
	}

	/** Total length of built road, in blocks. */
	public int builtLength() {
		int n = 0;
		for (RoadEdge e : this.edges) {
			if (e.emits()) {
				n += Math.min(e.built, e.length());
			}
		}
		return n;
	}

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		int[] ns = new int[this.nodes.size() * 4];
		for (Node n : this.nodes) {
			ns[n.id * 4] = n.x;
			ns[n.id * 4 + 1] = n.y;
			ns[n.id * 4 + 2] = n.z;
			ns[n.id * 4 + 3] = n.kind.ordinal();
		}
		t.putIntArray("nodes", ns);
		ListTag es = new ListTag();
		for (RoadEdge e : this.edges) {
			es.add(e.save());
		}
		t.put("edges", es);
		return t;
	}

	static RoadGraph load(CompoundTag t) {
		RoadGraph g = new RoadGraph();
		int[] ns = Nbt.ints(t, "nodes").orElse(new int[0]);
		NodeKind[] kinds = NodeKind.values();
		for (int i = 0; i + 3 < ns.length; i += 4) {
			g.addNode(ns[i], ns[i + 1], ns[i + 2], kinds[Math.min(kinds.length - 1, Math.max(0, ns[i + 3]))]);
		}
		for (Tag e : t.getListOrEmpty("edges")) {
			e.asCompound().ifPresent(c -> g.edges.add(RoadEdge.load(c)));
		}
		return g;
	}

	public enum NodeKind {
		HEART, JUNCTION, END, GATE
	}

	public record Node(int id, int x, int y, int z, NodeKind kind) {
	}
}
