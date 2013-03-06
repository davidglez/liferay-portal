package com.liferay.portal.lar;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.KeyValuePair;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.ClassedModel;
import com.liferay.portal.model.Role;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portlet.expando.model.ExpandoBridge;
import com.liferay.portlet.expando.util.ExpandoBridgeFactoryUtil;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/19/13
 * Time: 2:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class PortalDataHandlerImpl {

	public void exportClassedModel(ClassedModel classedModel, String parentPath,
		PortalDataContext portalDataContext) {

		String path = parentPath
			.concat(StringPool.SLASH)
			.concat(classedModel.getPrimaryKeyObj() + ".xml");

		portalDataContext.addZipEntry(path, classedModel);
	}

	public void exportExpandoBridgeAttributes(ClassedModel classedModel,
		String parentPath, PortalDataContext portalDataContext) {

		ExpandoBridge expandoBridge = classedModel.getExpandoBridge();

		Map<String, Serializable> expandoBridgeAttributes =
			expandoBridge.getAttributes();

		String path = parentPath
			.concat(StringPool.SLASH).concat("expando.xml");

		portalDataContext.addZipEntry(path, expandoBridgeAttributes);
	}

	public void exportPermissions(ClassedModel classedModel,
		PortalDataContext portalDataContext) {
		exportPermissions(classedModel, 0, portalDataContext);
	}

	public void exportPermissions(ClassedModel classedModel, long groupId,
		PortalDataContext portalDataContext) {

		try {
			long classPK = portalDataContext.getClassPK(classedModel);

			portalDataContext.setGroupId(groupId);

			portalDataContext.addPermissions(
				classedModel.getModelClassName(), classPK);

			_portalPermissionExporter.exportPortletDataPermissions(
				portalDataContext);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void exportRolesByGroupId(String path, long groupId,
		PortalDataContext portalDataContext){
		try{
			List<Role> roles = GroupLocalServiceUtil.fetchGroupRoles(groupId);

			Document document = SAXReaderUtil.createDocument();

			Element element = document.addElement("roles");

			for(Role role : roles){
				Element permissionElement = element.addElement("role");
				permissionElement.addAttribute("role-name", role.getName());
				permissionElement.addAttribute(
					"role-id", String.valueOf(role.getRoleId()));
			}

			String rolePath = path.concat(StringPool.SLASH).concat("roles.xml");

			try {
				portalDataContext.addZipEntry(
					rolePath, document.formattedString());
			}
			catch (Exception ioe) {
				throw new SystemException(ioe);
			}

		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	public void readPortalDataPermissions(PortalDataContext portalDataContext)
		throws PortalException {

		String xml = portalDataContext.getZipEntryAsString(
				"/portal-data-permissions.xml");

		if (xml == null) {
			return;
		}

		Document document = null;
		try {
			document = SAXReaderUtil.read(xml);
		} catch (DocumentException e) {
			throw new PortalException(
				"It has not been possible to read the file");
		}

		Element rootElement = document.getRootElement();

		List<Element> portalDataElements = rootElement.elements(
			"portal-data");

		for (Element portalDataElement : portalDataElements) {
			String resourceName = portalDataElement.attributeValue(
				"resource-name");
			long resourcePK = GetterUtil.getLong(
				portalDataElement.attributeValue("resource-pk"));

			List<KeyValuePair> permissions = new ArrayList<KeyValuePair>();

			List<Element> permissionsElements = portalDataElement.elements(
				"permissions");

			for (Element permissionsElement : permissionsElements) {
				String roleName = permissionsElement.attributeValue(
					"role-name");
				String actions = permissionsElement.attributeValue("actions");

				KeyValuePair permission = new KeyValuePair(roleName, actions);

				permissions.add(permission);
			}

			portalDataContext.addPermissions(
				resourceName, resourcePK, permissions);
		}
	}

	public void importRolesByGroupId(String path, long groupId,
			PortalDataContext portalDataContext)
		throws PortalException, SystemException {

		long companyId = portalDataContext.getCompanyId();

		String rolesPath = path.concat("/roles.xml");

		String xml = portalDataContext.getZipEntryAsString(
			rolesPath);

		Element rootElement;

		try {
			Document document = SAXReaderUtil.read(xml);
			rootElement = document.getRootElement();
		}
		catch (DocumentException e) {
			throw new SystemException("It has not been able to read the file");
		}

		List<Element> roles = rootElement.elements();

		for (Element roleElement : roles){
			String roleName = roleElement.attributeValue("role-name");

			Role role = RoleLocalServiceUtil.fetchRole(companyId, roleName);

			if(role != null){
				GroupLocalServiceUtil.addRoleGroups(
					role.getRoleId(), new long[]{groupId});
			}
		}
	}

	public void setExpandoBrige(PortalDataContext portalDataContext,
		ServiceContext serviceContext, String path, String className) {

		long companyId = portalDataContext.getCompanyId();

		Map<String, Serializable> importedExpando =
			new HashMap<String, Serializable>();

		Map<String, Serializable> expando =
			(Map<String, Serializable>)portalDataContext.getZipEntryAsObject(
				path + "/expando.xml");

		ExpandoBridge existingExpandoBridge =
			ExpandoBridgeFactoryUtil.getExpandoBridge(companyId, className);

		for (String key : expando.keySet()) {
			if(existingExpandoBridge.hasAttribute(key)) {
				importedExpando.put(key, expando.get(key));
			}
		}

		serviceContext.setExpandoBridgeAttributes(importedExpando);
	}

	protected PortalPermissionExporter _portalPermissionExporter =
		new PortalPermissionExporter();
    protected LayoutExporter _layoutExporter = new LayoutExporter();
	protected LayoutImporter _layoutImporter = new LayoutImporter();
}