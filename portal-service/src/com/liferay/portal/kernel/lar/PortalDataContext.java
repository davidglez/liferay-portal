package com.liferay.portal.kernel.lar;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.KeyValuePair;
import com.liferay.portal.kernel.zip.ZipWriter;
import com.liferay.portal.model.ClassedModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/19/13
 * Time: 9:35 AM
 * To change this template use File | Settings | File Templates.
 */
public interface PortalDataContext {

	public void addPermissions(String resourceName, long resourcePK)
		throws PortalException, SystemException;

	public void addPermissions(String resourceName, long resourcePK,
		List<KeyValuePair> permissions);

	public void addZipEntry(String path, Object object);

	public void addZipEntry(String path, String document);

	public Object fromXML(String xml);

	public Map<Long, Set<String>> getActionIds(long companyId, long[] roleIds,
			String className, long primKey, List<String> actionIds)
		throws PortalException, SystemException;

	public long getClassPK(ClassedModel classedModel);

	public long getCompanyId();

	public long getGroupId();

	public Map<String, List<KeyValuePair>> getPermissions();

	public Object getZipEntryAsObject(String path);

	public String getZipEntryAsString(String path);

	public ZipWriter getZipWriter();

	public void importPermissions(Class<?> clazz, long classPK, long newClassPK)
		throws PortalException, SystemException;

	public void importPermissions(String resourceObj, long resourcePK,
		long newResourcePK) throws PortalException, SystemException;

	public void importPermissions(ClassedModel classedModel,
			ClassedModel newClassedModel, String namespace)
		throws PortalException, SystemException;

	public void setGroupId(long groupId);

}
