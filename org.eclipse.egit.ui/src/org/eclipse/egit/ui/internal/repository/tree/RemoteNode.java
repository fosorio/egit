/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree;

import org.eclipse.jgit.lib.Repository;

/**
 * Represents the "Remote" node
 */
public class RemoteNode extends RepositoryTreeNode<String> {

	/**
	 * Constructs the node.
	 *
	 * @param parent
	 *            the parent node (may be null)
	 * @param repository
	 *            the {@link Repository}
	 * @param remoteName
	 *            the name of the remote specification
	 */
	public RemoteNode(RepositoryTreeNode parent, Repository repository,
			String remoteName) {
		super(parent, RepositoryTreeNodeType.REMOTE, repository, remoteName);
	}

}
