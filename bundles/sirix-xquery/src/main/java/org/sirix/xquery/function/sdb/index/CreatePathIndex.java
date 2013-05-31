package org.sirix.xquery.function.sdb.index;

import java.util.HashSet;
import java.util.Set;

import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.sirix.access.IndexController;
import org.sirix.api.NodeWriteTrx;
import org.sirix.exception.SirixIOException;
import org.sirix.index.IndexDef;
import org.sirix.index.IndexDefs;
import org.sirix.xquery.function.sdb.SDBFun;
import org.sirix.xquery.node.DBCollection;
import org.sirix.xquery.node.DBNode;

import com.google.common.collect.ImmutableSet;

/**
 * Function for creating path indexes on stored documents, optionally restricted
 * to a set of paths. If successful, this function returns statistics about the
 * newly created index as an XML fragment. Supported signatures are:</br>
 * <ul>
 * <li>
 * <code>bdb:create-path-index($coll as xs:string, $doc as xs:string, $paths as xs:string*) as 
 * node()</code></li>
 * <li><code>bdb:create-path-index($coll as xs:string, $doc as xs:string) as node()</code></li>
 * </ul>
 * 
 * @author Max Bechtold
 * 
 */
public final class CreatePathIndex extends AbstractFunction {

	public final static QNm CREATE_PATH_INDEX = new QNm(SDBFun.SDB_NSURI,
			SDBFun.SDB_PREFIX, "create-path-index");

	public CreatePathIndex(QNm name, Signature signature) {
		super(name, signature, true);
	}

	@Override
	public Sequence execute(final StaticContext sctx, final QueryContext ctx,
			final Sequence[] args) throws QueryException {
		if (args.length != 2 && args.length != 3) {
			throw new QueryException(new QNm("No valid arguments specified!"));
		}
		final DBCollection col = (DBCollection) ctx.getStore().lookup(
				((Str) args[0]).stringValue());
		
		if (col == null) {
			throw new QueryException(new QNm("No valid arguments specified!"));
		}
		
		IndexController controller = null;
		final Iter docs = col.iterate();
		DBNode doc = (DBNode) docs.next();
		try {
			while (doc != null) {
				if (doc.getName().getLocalName().equals(((Str) args[1]).stringValue())) {
					controller = doc.getTrx().getSession().getIndexController();
					break;
				}
				doc = (DBNode) docs.next();
			}
		} finally {
		 	docs.close();
		} 
		
		if (controller == null) {
			throw new QueryException(new QNm("Document not found: " + ((Str) args[1]).stringValue()));
		}
		
		final Set<Path<QNm>> paths = new HashSet<>();
		if (args.length > 2 && args[2] != null) {
			final Iter it = args[1].iterate();
			Item next = it.next();
			while (next != null) {
				paths.add(Path.parse(((Str) next).stringValue()));
				next = it.next();
			}
		}

		final IndexDef idxDef = IndexDefs.createPathIdxDef(paths);
		try {
			controller.createIndexes(ImmutableSet.of(idxDef), (NodeWriteTrx) doc.getTrx());
		} catch (final SirixIOException e) {
			throw new QueryException(new QNm("I/O exception: " + e.getMessage()), e);
		}
		return idxDef.materialize();
	}

}