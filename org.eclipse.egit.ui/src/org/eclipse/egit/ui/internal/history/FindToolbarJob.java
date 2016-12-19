/*******************************************************************************
 * Copyright (C) 2008, 2016 Roger C. Soares <rogersoares@intelinet.com.br> and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Thomas Wolf <thomas.wolf@paranor.ch> - rewrite as Job
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.ui.Activator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * This class executes the search function for the find toolbar. To avoid
 * consuming all the memory in the system, this class limits the maximum results
 * it stores.
 *
 * @see FindToolbar
 * @see FindResults
 */
public class FindToolbarJob extends Job {

	private static final int MAX_RESULTS = 20000;

	private static final ISchedulingRule SINGLE_JOB_RULE = new ISchedulingRule() {

		@Override
		public boolean contains(ISchedulingRule rule) {
			return this == rule;
		}

		@Override
		public boolean isConflicting(ISchedulingRule rule) {
			return this == rule;
		}
	};

	String pattern;

	SWTCommit[] fileRevisions;

	boolean ignoreCase;

	boolean findInCommitId;

	boolean findInComments;

	boolean findInAuthor;

	boolean findInCommitter;

	boolean findInReference;

	boolean findInCommitContent;

	private final FindResults findResults;


	/**
	 * Creates a new history search job.
	 *
	 * @param name
	 *            of the job
	 * @param findResults
	 *            to fill
	 */
	public FindToolbarJob(String name, FindResults findResults) {
		super(name);
		this.findResults = findResults;
		setRule(SINGLE_JOB_RULE);
	}

	private boolean find(String needle, String text) {
		if (text == null) {
			return false;
		}
		if (ignoreCase) {
			return text.toLowerCase().indexOf(needle) >= 0;
		}
		return text.indexOf(needle) >= 0;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		findResults.clear();

		if (pattern == null || pattern.isEmpty() || fileRevisions == null
				|| fileRevisions.length == 0) {
			return Status.OK_STATUS;
		}
		String findPattern = pattern;
		if (ignoreCase) {
			findPattern = pattern.toLowerCase();
		}

		int totalRevisions = fileRevisions.length;
		SubMonitor progress = SubMonitor.convert(monitor, totalRevisions);
		for (int i = 0; i < totalRevisions; i++) {
			if (progress.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (findResults.size() >= MAX_RESULTS) {
				findResults.setOverflow();
				break;
			}

			// Finds for the pattern in the revision history.
			SWTCommit revision = fileRevisions[i];
			try {
				revision.parseBody();
			} catch (IOException e) {
				Activator.logError("Error parsing body", e); //$NON-NLS-1$
				continue;
			}

			if (findInCommitId && find(findPattern, revision.getId().name())) {
				if (progress.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				findResults.add(i, revision);
				continue;
			}
			if (findInComments
					&& find(findPattern, revision.getFullMessage())) {
				if (progress.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				findResults.add(i, revision);
				continue;
			}

			if (findInAuthor && (find(findPattern,
					revision.getAuthorIdent().getName())
					|| find(findPattern,
							revision.getAuthorIdent().getEmailAddress()))) {
				if (progress.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				findResults.add(i, revision);
				continue;
			}

			if (findInCommitter && (find(findPattern,
					revision.getCommitterIdent().getName())
					|| find(findPattern,
							revision.getCommitterIdent().getEmailAddress()))) {
				if (progress.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				findResults.add(i, revision);
				continue;
			}

			if (findInReference) {
				for (int j = 0; j < revision.getRefCount(); j++) {
					if (progress.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					Ref ref = revision.getRef(j);
					String refName = ref.getName();
					refName = Repository.shortenRefName(refName);
					if (find(findPattern, refName)) {
						if (progress.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						findResults.add(i, revision);
						break;
					}
				}
			}

			if (findInCommitContent
					&& findInCommitContent(findPattern, revision)) {

				if (progress.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				findResults.add(i, revision);
				continue;
			}

			progress.worked(1);
		}
		return progress.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
	}

	private boolean findInCommitContent(String findPattern,
			SWTCommit revision) {

		Repository repository = (Repository) revision.getAdapter(Repository.class);

   		try (ObjectReader reader = repository.newObjectReader()) {
       		CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			oldTreeIter.reset(reader, revision.getParent(0).getId());
       		CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, revision.getId());

       		// finally get the list of changed files
       		try (Git git = new Git(repository)) {
                   List<DiffEntry> diffs= git.diff()
           		                    .setNewTree(newTreeIter)
           		                    .setOldTree(oldTreeIter)
           		                    .call();
                   for (DiffEntry entry : diffs) {

					if (matchesPickaxe(findPattern, entry, repository))
						return true;

                   }
			} catch (GitAPIException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IncorrectObjectTypeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private static boolean matchesPickaxe(String pattern, DiffEntry entry,
			Repository repository) throws MissingObjectException, IOException {

		int ocurrencesInParent = countOcurrences(pattern, entry.getOldId(),
						repository);
		int ocurrencesInCurrent = countOcurrences(pattern, entry.getNewId(),
						repository);

		return ocurrencesInParent != ocurrencesInCurrent;
	}

	private static int countOcurrences(String pattern, AbbreviatedObjectId id,
			Repository repository) throws MissingObjectException, IOException {

		ObjectLoader loader = repository.open(id.toObjectId());

		// and then one can the loader to read the file
		String str = new String(loader.getCachedBytes());

		int lastIndex = 0;
		int count = 0;

		while (lastIndex != -1) {

			lastIndex = str.indexOf(pattern, lastIndex);

			if (lastIndex != -1) {
				count++;
				lastIndex += pattern.length();
			}
		}

		return count;

}


}
