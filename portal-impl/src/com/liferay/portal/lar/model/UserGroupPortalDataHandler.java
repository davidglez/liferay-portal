package com.liferay.portal.lar.model;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.zip.ZipReader;
import com.liferay.portal.kernel.zip.ZipReaderFactoryUtil;
import com.liferay.portal.kernel.zip.ZipWriter;
import com.liferay.portal.kernel.zip.ZipWriterFactoryUtil;
import com.liferay.portal.lar.PortalDataContextImpl;
import com.liferay.portal.lar.PortalDataHandlerImpl;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserGroupLocalServiceUtil;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/27/13
 * Time: 11:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class UserGroupPortalDataHandler extends PortalDataHandlerImpl {

	public File exportUserGroupAsFile(long companyId,
			Map<String, String[]> parameterMap, long userGroupId)
		throws PortalException, SystemException {

		PortalDataContext portalDataContext =
			new PortalDataContextImpl(companyId);

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("root");

		Element headerElement = rootElement.addElement("header");

		headerElement.addAttribute(
			"build-number", String.valueOf(ReleaseInfo.getBuildNumber()));
		headerElement.addAttribute("export-date", Time.getRFC822());
		headerElement.addAttribute("type", "usergroup");

		UserGroup userGroup =
			UserGroupLocalServiceUtil.getUserGroup(userGroupId);

		Element usergGroupElement = rootElement.addElement("usergroup");

		String userGroupsRootPath = "/usergroup";

		File layoutZipFile = new File(
			portalDataContext.getZipWriter().getPath() + "/layout.zip");

		ZipWriter layoutZipWriter =
			ZipWriterFactoryUtil.getZipWriter(layoutZipFile);

		String userGroupRootPath = userGroupsRootPath + "/" + userGroupId;
		usergGroupElement.addAttribute(
			"usergroup-id", String.valueOf(userGroupId));
		usergGroupElement.addAttribute(
			"usergroup-name", userGroup.getName());
		usergGroupElement.addAttribute("path", userGroupRootPath);

		try {
			exportUserGroup(userGroupRootPath, userGroup, portalDataContext,
				parameterMap, layoutZipWriter);
		} catch (Exception e) {
			e.printStackTrace();
			throw new PortalException(
				"It has been able to export the UserGroup");
		}

		try {
			portalDataContext.addZipEntry(
				"manifest.xml", document.formattedString());
		}
		catch (IOException e) {
			throw new SystemException("There has been a problem");
		}

		return portalDataContext.getZipWriter().getFile();
	}

	protected void exportUserGroup(String path, UserGroup userGroup,
			PortalDataContext portalDataContext,
			Map<String, String[]> parameterMap, ZipWriter zipWriter)
		throws Exception{

		exportClassedModel(userGroup, path, portalDataContext);

		long groupId = userGroup.getGroup().getGroupId();

		List<Layout> layouts = LayoutLocalServiceUtil.getLayouts(groupId, false);
		long[] layoutIds = getLayoutIds(layouts);

		_layoutExporter.doBaseExportLayoutsAsFile(
			groupId, false, layoutIds, parameterMap, null, null, zipWriter);

	}

	private long[] getLayoutIds(List<Layout> layouts) {
		long[] layoutIds = new long[layouts.size()];

		for(int i = 0; i < layouts.size(); i++) {
			layoutIds[i] = layouts.get(i).getLayoutId();
		}

		return layoutIds;
	}

	public void importUserGroup(long companyId,
			Map<String, String[]> parameterMap, long userId, File file)
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
		catch (Exception e) {
			e.printStackTrace();
		}

		Element headerElement = rootElement.element("header");

		int buildNumber = ReleaseInfo.getBuildNumber();

		int importBuildNumber = GetterUtil.getInteger(
			headerElement.attributeValue("build-number"));

		if (buildNumber != importBuildNumber) {
			throw new PortalException(
				"The build number does not match");
		}

		String type = headerElement.attributeValue("type");

		if (!type.equals("usergroup")) {
			throw new PortalException(
				"The type of model does not correspond with an user group");
		}

		Element userGroupElement = rootElement.element("usergroup");

		String path = userGroupElement.attributeValue("path");

		String userGroupName =
			userGroupElement.attributeValue("usergroup-name");
		long userGroupId =
			Long.parseLong(userGroupElement.attributeValue("usergroup-id"));
		try {
			UserGroup importedUserGroup = doImportUserGroup(
				portalDataContext, path, userGroupName, userGroupId, userId);

			if (importedUserGroup != null){
				long groupId = importedUserGroup.getGroup().getGroupId();
				portalDataContext.setGroupId(groupId);

				importLayout(portalDataContext, file, parameterMap, userId);
			}
		}
		catch (Exception e){
			throw new PortalException(
				"There has been an error importing the file");
		}
	}

	protected UserGroup doImportUserGroup(PortalDataContext portalDataContext,
			String path, String userGroupName, long userGroupId, long userId)
		throws PortalException, SystemException{

		long companyId = portalDataContext.getCompanyId();

		UserGroup existingUserGroup =
			UserGroupLocalServiceUtil.fetchUserGroup(companyId, userGroupName);

		UserGroup userGroup =
			(UserGroup)portalDataContext.getZipEntryAsObject(
				path + "/" + userGroupId + ".xml");

		ServiceContext serviceContext = new ServiceContext();

		if (existingUserGroup != null){
			return UserGroupLocalServiceUtil.updateUserGroup(
				companyId, existingUserGroup.getUserGroupId(),
				existingUserGroup.getName(), userGroup.getDescription(),
				serviceContext);
		} else {
			return UserGroupLocalServiceUtil.addUserGroup(
				userId, companyId, userGroup.getName(),
				userGroup.getDescription(), serviceContext);

		}

	}

	protected void importLayout(PortalDataContext portalDataContext,
			File file, Map<String, String[]> parameterMap, long userId)
		throws Exception {

		String layoutPath = file.getPath() + "/layout.zip";
		File layoutFile = new File(layoutPath);

		long groupId = portalDataContext.getGroupId();

		_layoutImporter.importLayouts(
			userId, groupId, false, parameterMap, layoutFile);
	}
}
