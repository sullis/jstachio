package io.jstach.jstachio.context;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.Nullable;

/**
 * This interface serves three puproses:
 *
 * <ol>
 * <li>A way to represent the current context stack (see {@link #parent()})
 * <li>Allow you to simulate JSON/Javscript object node like trees without being coupled
 * to a particularly JSON lib.
 * <li>Represent per request context data in a web framework like CSRF tokens.
 * </ol>
 * The interface simply wraps {@link Map} and {@link Iterable} (and arrays) lazily through
 * composition but generally cannot wrap other context nodes. If an object is wrapped that
 * is not a Map or Iterable it becomes a leaf node similar to JSON.
 * <p>
 * It is not recommended you use this interface as it avoids much of the type checking
 * safty of this library, decreases performance as well as increase coupling however it
 * does provide a slightly better bridge to legacy {@code Map<String,?>} models over using
 * the maps directly.
 * <p>
 * Context Node while similar to a Map does not follow the same rules of resolution where
 * Map resolves bindings always last. It will resolve first and thus it is easy to
 * accidentally get stuck in the Context Node context. To prevent this it is highly
 * recommended you do not open a context node with a section block and prefer dotted
 * notation to access it.
 *
 * <h2>Example:</h2>
 *
 * <pre><code class="language-hbs">
 * {{message}}
 * {{#&#64;context}}
 * {{message}} {{! message here will only ever resolve against &#64;context and not the parent }}
 * {{/&#64;context}}
 * </code> </pre>
 *
 * @apiNote The parents do not know anything about their children as it is the child that
 * has reference to the parent. This interface unlike most of JStachio API is very 
 * <code>null</code> heavy because JSON and Javascript allow null.
 * @author agentgt
 *
 */
public interface ContextNode extends Iterable<@Nullable ContextNode> {

	/**
	 * Creates a root context node with the given function to look up children.
	 * @param function used to find children with a given name
	 * @return root context node powered by a function
	 * @apiNote Unlike many other methods in this class this is not nullable.
	 */
	public static ContextNode of(Function<String, ?> function) {
		if (Objects.isNull(function)) {
			throw new NullPointerException("function is required");
		}
		return new FunctionContextNode(function);
	}

	/**
	 * An empty context node that is safe to use identify comparison.
	 * @return empty singleton context node
	 */
	public static ContextNode empty() {
		return EmptyContextNode.EMPTY;
	}

	/**
	 * Resolves the context node from an object.
	 * @param o object that maybe a context or have a context.
	 * @return {@link #empty()} if not found.
	 */
	public static ContextNode resolve(Object o) {
		if (o instanceof ContextSupplier cs) {
			return cs.context();
		}
		if (o instanceof ContextNode n) {
			return n;
		}
		return ContextNode.empty();
	}
	
	/**
	 * Resolves the context node trying first and then second.
	 * @param first first object to try
	 * @param second second object to try
	 * @return {@link #empty()} if not found.
	 */
	public static ContextNode resolve(Object first, Object second) {
		var f = resolve(first);
		if (f == ContextNode.empty()) {
			return resolve(second);
		}
		return f;
	}

	/**
	 * Internal for suppress unused warnings for context node variable.
	 * @param node node
	 * @apiNote ignore. For code generation purposes.
	 */
	public static void suppressUnused(ContextNode node) {
	}

	/**
	 * Creates the root node which has no name.
	 * @apiNote Unlike the other methods in this class if the passed in object is a
	 * context node it is simply returned if it is a root node otherwise it is rewrapped.
	 * @param o the object to be wrapped. Maybe <code>null</code>.
	 * @return <code>null</code> if the root object is null otherwise a new root node.
	 * @deprecated This method is slated for removal as it confusing. Prefer {@link #of(Function)}
	 * or implement the interface.
	 */
	public static @Nullable ContextNode ofRoot(@Nullable Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof ContextNode n) {
			if (n.parent() != null) {
				return ofRoot(n.object());
			}
			return n;
		}
		return new RootContextNode(o);
	}

	/**
	 * Creates a named child node off of this node where the return child nodes parent
	 * will be this node.
	 * @param name the context name.
	 * @param o the object to be wrapped.
	 * @return <code>null</code> if the child object is null otherwise a new child node.
	 * @throws IllegalArgumentException if the input object is a {@link ContextNode}
	 * @deprecated with no planned replacement. If you rely on this please file an issue.
	 */
	@Deprecated
	default @Nullable ContextNode ofChild(String name, @Nullable Object o) throws IllegalArgumentException {
		if (o == null) {
			return null;
		}
		if (o instanceof ContextNode) {
			throw new IllegalArgumentException("Cannot wrap ContextNode around another ContextNode");
		}
		return new NamedContextNode(this, o, name);
	}

	/**
	 * Creates an indexed child node off of this node where the return child nodes parent
	 * will be this node.
	 * @param index a numeric index
	 * @param o the object to be wrapped. Maybe <code>null</code>.
	 * @return <code>null</code> if the child object is null otherwise a new child node.
	 * @throws IllegalArgumentException if the input object is a {@link ContextNode}
	 * @apiNote there is no checking to see if the same index is reused as the parent
	 * knows nothing of the child.
	 * @deprecated with no planned replacement. If you rely on this please file an issue.
	 */
	@Deprecated
	default @Nullable ContextNode ofChild(int index, @Nullable Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof ContextNode) {
			throw new IllegalArgumentException("Cannot wrap ContextNode around another ContextNode");
		}
		return new IndexedContextNode(this, o, index);
	}

	/**
	 * Gets a field from a ContextNode. This is direct
	 * access (end of a dotted path) and does not check the parents.
	 * The default implementation will check if the wrapping object is
	 * a {@link Map} and use it to return a child context node.
	 *
	 * Just like {@link Map} <code>null</code> will be returned if no field is found.
	 * @param field the name of the field
	 * @return a new child node. Maybe <code>null</code>.
	 */
	default @Nullable ContextNode get(String field) {
		Object o = object();
		ContextNode child = null;
		if (o instanceof Map<?, ?> m) {
			child = ofChild(field, m.get(field));
		}
		return child;
	}

	/**
	 * Will search up the tree for a field starting at this nodes children first.
	 * @param field context name (e.g. section name)
	 * @return <code>null</code> if not found otherwise creates a new node from the map or
	 * object containing the field.
	 */
	default @Nullable ContextNode find(String field) {
		/*
		 * In theory we could make a special RenderingContext for ContextNode to go up the
		 * stack (generated code) but it would probably look similar to the following.
		 */
		ContextNode child = get(field);
		if (child != null) {
			return child;
		}
		var parent = parent();
		if (parent != null && parent != this) {
			child = parent.find(field);
			if (child != null) {
				child = ofChild(field, child.object());
			}
		}
		return child;
	}

	/**
	 * The object being wrapped.
	 * @return the Map, Iterable or object that was wrapped. Never <code>null</code>.
	 */
	public Object object();

	/**
	 * Convenience method for calling <code>toString</code> on the wrapped object.
	 * @return a toString on the wrapped object.
	 */
	default String renderString() {
		return String.valueOf(object());
	}

	/**
	 * The parent node.
	 * @return the parent node or <code>null</code> if this is the root.
	 */
	default @Nullable ContextNode parent() {
		return null;
	}

	/**
	 * If the node is a Map or a non iterable/array a singleton iterator will be returned.
	 * Otherwise if it is an interable/array new child context nodes will be created
	 * lazily.
	 * @return lazy iterator of context nodes.
	 */
	@SuppressWarnings("exports")
	@Override
	default Iterator<@Nullable ContextNode> iterator() {
		Object o = object();
		if (o instanceof Iterable<?> it) {
			int[] j = {-1};
			return StreamSupport.stream(it.spliterator(), false).map(i -> this.ofChild((j[0] += 1), i))
					.iterator();
		}
		else if (o == null || Boolean.FALSE.equals(o)) {
			return Collections.emptyIterator();
		}
		else if (o.getClass().isArray()) {

			Stream<? extends @Nullable Object> s = arrayToStream(o);
			int[] j = {-1};
			return s.map(i -> this.ofChild((j[0] += 1), i)).iterator();
		}

		return Collections.<@Nullable ContextNode>singletonList(this).iterator();
	}

	@SuppressWarnings("null")
	private static Stream<? extends @Nullable Object> arrayToStream(
			Object o) {
		/*
		 * There is probably an easier way to do this
		 */
		final Stream<? extends @Nullable Object> s;
		if (o instanceof int[] a) {
			s = Arrays.stream(a).boxed();
		}
		else if (o instanceof long[] a) {
			s = Arrays.stream(a).boxed();
		}
		else if (o instanceof double[] a) {
			s = Arrays.stream(a).boxed();
		}
		else if (o instanceof boolean[] a) {
			List<Boolean> b = new ArrayList<>();
			for (var _a : a) {
				b.add(_a);
			}
			s = b.stream();
		}
		else if (o instanceof char[] a) {
			List<Character> b = new ArrayList<>();
			for (var _a : a) {
				b.add(_a);
			}
			s = b.stream();
		}
		else if (o instanceof byte[] a) {
			List<Byte> b = new ArrayList<>();
			for (var _a : a) {
				b.add(_a);
			}
			s = b.stream();
		}
		else if (o instanceof float[] a) {
			List<Float> b = new ArrayList<>();
			for (var _a : a) {
				b.add(_a);
			}
			s = b.stream();
		}
		else if (o instanceof short[] a) {
			List<Short> b = new ArrayList<>();
			for (var _a : a) {
				b.add(_a);
			}
			s = b.stream();
		}
		else if (o instanceof Object[] a) {
			s = Arrays.asList(a).stream();
		}
		else {
			throw new IllegalArgumentException("array type not supported: " + o.getClass());
		}
		return s;
	}

	/**
	 * Determines if an object is falsey based on mustache spec semantics where:
	 * <code>null</code>, empty iterables, empty arrays and boolean <code>false</code> are
	 * falsey however <strong>empty Map is not falsey</strong>.
	 * @param context a context object. ContextNode are allowed as input as well as
	 * <code>null</code>.
	 * @return true if the object is falsey.
	 */
	static boolean isFalsey(@Nullable Object context) {
		if ((context == null) || Boolean.FALSE.equals(context)) {
			return true;
		}
		if (context instanceof Collection<?> c) {
			return c.isEmpty();
		}
		if (context instanceof Iterable<?> it) {
			return !it.iterator().hasNext();
		}
		if (context.getClass().isArray() && Array.getLength(context) == 0) {
			return true;
		}
		return false;
	}
	
}

interface SingleNode extends ContextNode {
	@Override
	default Iterator<@Nullable ContextNode> iterator() {
		return Collections.<@Nullable ContextNode>singleton(this).iterator();
	}
}

interface ObjectContextNode extends SingleNode {
	
	public @Nullable Object getValue(String key);
	
	@Override
	default @Nullable ContextNode get(
			String field) {
		return ofChild(field, getValue(field));
	}
	
	@SuppressWarnings("exports")
	@Override
	default Iterator<@Nullable ContextNode> iterator() {
		return Collections.<@Nullable ContextNode>singleton(this).iterator();
	}
}

interface ListNode extends ContextNode {
	@Override
	default @Nullable ContextNode get(
			String field) {
		return null;
	}
	
	@Override
	public Iterator<@Nullable ContextNode> iterator();
	
}

record NamedContextNode(ContextNode parent, Object object, String name) implements ContextNode {
	@Override
	public String toString() {
		return renderString();
	}
}

record IndexedContextNode(ContextNode parent, Object object, int index) implements ContextNode {
	@Override
	public String toString() {
		return renderString();
	}
}

record IterableContextNode(
		Iterable<?> object, ContextNode parent) implements ListNode {

	@Override
	public Iterator<@Nullable ContextNode> iterator() {
		int[] j = { -1 };
		var it = object();
		return StreamSupport.stream(it.spliterator(), false)
			.map(i -> this.ofChild((j[0] += 1), i))
			.iterator();
	}

}

record RootContextNode(Object object) implements ContextNode {
	@Override
	public String toString() {
		return renderString();
	}
}

record FunctionContextNode(Function<String, ?> object) implements ObjectContextNode {
	@Override
	public String toString() {
		return renderString();
	}

	@Override
	public @Nullable Object getValue(
			String key) {
		return object.apply(key);
	}
}


enum EmptyContextNode implements ContextNode {

	EMPTY;

	@Override
	public Object object() {
		return Map.of();
	}
	
	@Override
	public @Nullable ContextNode get(
			String field) {
		return null;
	}
	
	@Override
	public @Nullable ContextNode find(
			String field) {
		return null;
	}
	
	@Override
	public Iterator<@Nullable ContextNode> iterator() {
		/*
		 * TODO should this be empty?
		 */
		return Collections.<@Nullable ContextNode>singleton(this).iterator();
	}

}
