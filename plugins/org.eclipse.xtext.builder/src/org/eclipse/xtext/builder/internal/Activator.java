/*******************************************************************************
 * Copyright (c) 2009 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.builder.internal;

import java.io.File;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.xtext.builder.BuilderModule;
import org.eclipse.xtext.builder.builderState.IBuilderState;
import org.eclipse.xtext.builder.impl.javasupport.JdtBuilderModule;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class Activator extends AbstractUIPlugin {
	
	private final static Logger log = Logger.getLogger(Activator.class);

	private static Activator INSTANCE;
	
	private static final String BUILDER_STATE = "builder.state";
	
	public static Activator getDefault() {
		return INSTANCE;
	}

	private Injector injector;
	
	public Injector getInjector() {
		return injector;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		INSTANCE = this;
		try {
			injector = Guice.createInjector(createBuilderModule(context));
			final IBuilderState state = injector.getInstance(IBuilderState.class);
			File file = getBuilderStateLocation().toFile();
			try {
				if (file.exists()) {
					state.load();
				} else {
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
				}
			} catch(Exception e) {
				log.error("Error while loading persistable builder state.", e);
				log.error("Triggering a full build.");
				ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
			} finally {
				if (file.exists())
					file.delete();
				if (PlatformUI.isWorkbenchRunning()) {
					IWorkbench workbench = PlatformUI.getWorkbench();
					if (workbench != null) {
						workbench.addWorkbenchListener(new IWorkbenchListener() {
							public boolean preShutdown(IWorkbench workbench, boolean forced) {
								try {
									state.save();
								} catch (Exception e) {
									log.error("Error while saving persistable builder state", e);
									log.error("Deleting builder state from disk...");
									File file = getBuilderStateLocation().toFile();
									if (file.exists())
										file.delete();
								}
								return true;
							}
	
							public void postShutdown(IWorkbench workbench) {
								// do nothing.
							}
						});
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw e;
		} catch (Error e) {
			log.error(e.getMessage(), e);
			throw e;
		}
	}

	protected Module createBuilderModule(BundleContext context) {
		String javaCorePlugin = JavaCore.PLUGIN_ID;
		for(Bundle bundle: context.getBundles()) {
			if (javaCorePlugin.equals(bundle.getSymbolicName()))
				return new JdtBuilderModule(); 
		}
		return new BuilderModule(); 
	}
	
	public IPath getBuilderStateLocation() {
		IPath stateLocation = getStateLocation();
		IPath result = stateLocation.append(BUILDER_STATE);
		return result;
	}
	
	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
		INSTANCE = null;
	}
}
