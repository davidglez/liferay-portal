package com.liferay.portal.lar;

import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.KeyValuePair;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/19/13
 * Time: 9:28 AM
 * To change this template use File | Settings | File Templates.
 */
public class PortalPermissionExporter {

	public void exportPortletDataPermissions(
			PortalDataContext portalDataContext)
		throws Exception {

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("portal-data-permissions");


		Map<String, List<KeyValuePair>> permissionsMap =
			portalDataContext.getPermissions();

		for (Map.Entry<String, List<KeyValuePair>> entry :
			permissionsMap.entrySet()) {

			String[] permissionParts = StringUtil.split(
				entry.getKey(), CharPool.POUND);

			String resourceName = permissionParts[0];
			long resourcePK = GetterUtil.getLong(permissionParts[1]);

			Element portletDataElement = rootElement.addElement("portal-data");

			portletDataElement.addAttribute("resource-name", resourceName);
			portletDataElement.addAttribute(
				"resource-pk", String.valueOf(resourcePK));

			List<KeyValuePair> permissions = entry.getValue();

			for (KeyValuePair permission : permissions) {
				String roleName = permission.getKey();
				String actions = permission.getValue();

				Element permissionsElement = portletDataElement.addElement(
					"permissions");

				permissionsElement.addAttribute("role-name", roleName);
				permissionsElement.addAttribute("actions", actions);
			}
		}

		portalDataContext.addZipEntry("/portal-data-permissions.xml",
			document.formattedString());
	}
}
