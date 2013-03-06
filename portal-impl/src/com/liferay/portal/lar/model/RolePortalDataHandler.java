package com.liferay.portal.lar.model;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.zip.ZipReader;
import com.liferay.portal.kernel.zip.ZipReaderFactoryUtil;
import com.liferay.portal.lar.PortalDataContextImpl;
import com.liferay.portal.lar.PortalDataHandlerImpl;
import com.liferay.portal.model.ResourceAction;
import com.liferay.portal.model.ResourcePermission;
import com.liferay.portal.model.Role;
import com.liferay.portal.service.ResourceActionLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;

import java.io.File;
import java.io.IOException;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/15/13
 * Time: 9:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class RolePortalDataHandler extends PortalDataHandlerImpl {

	public File exportRoleAsFile(long companyId, long roleId)
		throws PortalException, SystemException {

		PortalDataContext portalDataContext =
			new PortalDataContextImpl(companyId);

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("root");

		Element headerElement = rootElement.addElement("header");

		headerElement.addAttribute(
			"build-number", String.valueOf(ReleaseInfo.getBuildNumber()));
		headerElement.addAttribute("export-date", Time.getRFC822());
		headerElement.addAttribute("type", "role");

		Role role = RoleLocalServiceUtil.getRole(roleId);
		Element elementRoles = rootElement.addElement("roles");

		String rolesRootPath = "/roles";

		String roleRootPath = rolesRootPath + "/" + roleId;
		Element elementRole = elementRoles.addElement("role");
		elementRole.addAttribute(
			"role-id", String.valueOf(roleId));
		elementRole.addAttribute("role-name", role.getName());
		elementRole.addAttribute("path", roleRootPath);

		exportRole("/roles", role, portalDataContext);

		try {
			portalDataContext.addZipEntry(
				"manifest.xml", document.formattedString());

			return portalDataContext.getZipWriter().getFile();
		}
		catch (IOException ioe) {
			throw new SystemException(ioe);
		}
	}

	protected void exportRole(String parentPath, Role role,
		PortalDataContext portalDataContext)
		throws SystemException, PortalException {

		long roleId = role.getRoleId();

		String path = parentPath
			.concat(StringPool.SLASH)
			.concat(String.valueOf(roleId));

		exportClassedModel(role, path, portalDataContext);

		exportRoleDefinedPermissions(roleId, path, portalDataContext);

		exportPermissions(role, portalDataContext);

	}

	protected void exportRoleDefinedPermissions(long roleId, String parentPath,
		PortalDataContext portalDataContext){

		try {
			Document document = SAXReaderUtil.createDocument();

			List<ResourcePermission> resourcePermissions =
				ResourcePermissionLocalServiceUtil
					.getRoleResourcePermissions(roleId);

			Element element = document.addElement("defined-permissions");

			for(ResourcePermission resourcePermission : resourcePermissions){

				String resourcePermissionName = resourcePermission.getName();

				Element modelElement = element.addElement("model");
                modelElement.addAttribute("model-name", resourcePermissionName);

				List<ResourceAction> resourceActions =
					ResourceActionLocalServiceUtil
						.getResourceActions(resourcePermissionName);

				for (ResourceAction resourceAction : resourceActions) {

					if(ResourcePermissionLocalServiceUtil.hasActionId(
						resourcePermission, resourceAction)){
						String actionId = resourceAction.getActionId();

						Element actionElement =
							modelElement.addElement("action");
						actionElement.addAttribute("scope", String.valueOf(
							resourcePermission.getScope()));
						actionElement.addText(actionId);
					}
				}
			}

			String path = parentPath
				.concat(StringPool.SLASH).concat("defined-permissions.xml");

			try {
				portalDataContext.addZipEntry(
					path, document.formattedString());
			}
			catch (Exception ioe) {
				throw new SystemException(ioe);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void importRole(long companyId, File file)
		throws PortalException, SystemException {
		ZipReader zipReader = ZipReaderFactoryUtil.getZipReader(file);

		PortalDataContext portalDataContext =
			new PortalDataContextImpl(companyId, zipReader);

		String xml = portalDataContext.getZipEntryAsString(
			"/manifest.xml");

		Element rootElement = null;

		try {
			Document document = SAXReaderUtil.read(xml);
			rootElement = document.getRootElement();
		}
		catch (DocumentException e) {
			throw new SystemException(
				"It has been impossible to read from the file");
		}

		Element headerElement = rootElement.element("header");

		int buildNumber = ReleaseInfo.getBuildNumber();

		int importBuildNumber = GetterUtil.getInteger(
			headerElement.attributeValue("build-number"));

		if (buildNumber != importBuildNumber) {
			throw new SystemException("The import build number does not match");
		}

		String type = headerElement.attributeValue("type");

		if (!type.equals("role")) {
			throw new SystemException(
				"The file does not correspond with a role");
		}

		readPortalDataPermissions(portalDataContext);

		Element rolesElement =
			rootElement.element("roles");

		List<Element> roleElements = rolesElement.elements();

		for (Element roleElement : roleElements){

			String path = roleElement.attributeValue("path");

			String roleName =
				roleElement.attributeValue("role-name");
			long roleId =
				Long.parseLong(roleElement.attributeValue("role-id"));

			doImportRole(portalDataContext, path, roleName, roleId);
		}

	}

	protected void doImportRole(PortalDataContext portalDataContext,
			String path, String roleName, long roleId)
		throws PortalException, SystemException {

		long companyId = portalDataContext.getCompanyId();

		Role existingRole = RoleLocalServiceUtil.fetchRole(companyId, roleName);

		ServiceContext serviceContext = new ServiceContext();

		Role importedRole = null;

		Role role =
			(Role)portalDataContext.getZipEntryAsObject(
				path + "/" + roleId + ".xml");

		if (existingRole != null){
			try {
				importedRole = RoleLocalServiceUtil.updateRole(
					existingRole.getRoleId(), existingRole.getName(),
					role.getTitleMap(), role.getDescriptionMap(),
					role.getSubtype(), serviceContext);
			} catch (Exception e){
				e.printStackTrace();
			}
		} else {
			try {
				importedRole = RoleLocalServiceUtil.addRole(role);
			} catch (Exception e){
				e.printStackTrace();
			}
		}

		importDefinedPermissions(path, portalDataContext, role);

		portalDataContext.importPermissions(role, importedRole, "role");

	}

	protected void importDefinedPermissions(
			String path, PortalDataContext portalDataContext, Role role)
		throws PortalException, SystemException {

		long companyId = portalDataContext.getCompanyId();
		long roleId = role.getRoleId();

		String xml = portalDataContext.getZipEntryAsString(
			path + "/defined-permissions.xml");

		Element rootElement = null;

		try {
			Document document = SAXReaderUtil.read(xml);
			rootElement = document.getRootElement();
		}
		catch (DocumentException e) {
			throw new SystemException(
				"It has been impossible to read from the file");
		}

		List<Element> modelNameElements = rootElement.elements();

		for(Element modelNameElement : modelNameElements) {

			String modelName = modelNameElement.attributeValue("model-name");

			List<Element> actionElements = modelNameElement.elements();

			for (Element actionElement : actionElements) {

				int scope =
					Integer.parseInt(actionElement.attributeValue("scope"));
				String actionId = actionElement.getText();

				ResourcePermissionLocalServiceUtil.addResourcePermission(
					companyId, modelName, scope, String.valueOf(companyId),
					roleId, actionId);
			}
		}
	}

}
