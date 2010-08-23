/*
 *   This software is distributed under the terms of the FSF 
 *   Gnu Lesser General Public License (see lgpl.txt). 
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.autoloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.DuplicateMemberException;

import com.scooterframework.admin.ApplicationConfig;
import com.scooterframework.common.logging.LogUtil;
import com.scooterframework.common.util.StringUtil;
import com.scooterframework.orm.activerecord.ActiveRecord;

/**
 * ClassWorkHelper class knows how to modify class byte codes.
 * 
 * @author (Fei) John Chen
 */
public class ClassWorkHelper {
    protected LogUtil log = LogUtil.getLogger(this.getClass().getName());
	private ClassPool pool;
	private ClassLoader cl;

	public ClassWorkHelper(ClassLoader cl) {
		this.cl = cl;
		pool = new ClassPool();
		pool.appendSystemPath();
		try {
			pool.appendPathList(System.getProperty("java.class.path"));
			pool.appendClassPath(ApplicationConfig.getInstance().getClassFileLocationPath());
		} catch (NotFoundException ex) {
			log.error("Error finding classpath: " + ex.getMessage());
		}
	}
	
	public Class changeClass(String className) throws ClassNotFoundException {
		Class result = null;

		try {
			CtClass cc = pool.get(className);

			if (cc.subclassOf(pool.get(ActiveRecord.class.getName()))) {
				addMethods(cc, ClassWorkSource.arMethods);
			}
			
			if (ApplicationConfig.getInstance().isWebApp()) {
				result = cc.toClass(cl, this.getClass().getProtectionDomain());
			} else {
				result = cc.toClass();
				//result = cc.toClass(cl, this.getClass().getProtectionDomain());
			}
			
			cc.defrost();
		} catch (Exception ex) {
			throw new ClassNotFoundException("classWork failed on " + className + ": " + ex.getMessage());
		}

		return result;
	}
    
    public byte[] changeClassBytes(String className, byte[] bytes) 
    throws ClassNotFoundException {
    	byte[] result = null;
    	CtClass cc = null;
        try {
        	cc = pool.makeClass(new ByteArrayInputStream(bytes));
			
			if (cc.subtypeOf(pool.get(ActiveRecord.class.getName()))) {
				addMethods(cc, ClassWorkSource.arMethods);
				result = cc.toBytecode();
			}
			else {
				result = bytes;
			}
		} catch (Exception ex) {
			throw new ClassNotFoundException("classWork failed on " + className + ": " + ex.getMessage());
		}
		
		return result;
    }
    
	private void addMethods(CtClass cc, List methods) throws CannotCompileException, IOException {
		cc.defrost();
		CtMethod m = null;
		Iterator it = methods.iterator();
		while (it.hasNext()) {
			String method = (String) it.next();
			m = CtMethod.make(filterSourceCode(method, cc.getName()), cc);
			try {
				cc.addMethod(m);
			}
			catch(DuplicateMemberException dme) {
				log.warn(dme.getMessage() + " duplicated with source \"" + method +"\".");
			}
		}
	}
	
	private String filterSourceCode(String src, String className) {
		if (src.indexOf("@@") != -1) {
			src = StringUtil.replace(src, "@@", className);
		}
		return src;
	}
}