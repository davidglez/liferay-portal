package com.liferay.portal.lar;

import com.liferay.portal.NoSuchRoleException;
import com.liferay.portal.NoSuchTeamException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.KeyValuePair;
import com.liferay.portal.kernel.util.PrimitiveLongList;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.zip.ZipReader;
import com.liferay.portal.kernel.zip.ZipWriter;
import com.liferay.portal.kernel.zip.ZipWriterFactoryUtil;
import com.liferay.portal.model.ClassedModel;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.ResourcedModel;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.model.Team;
import com.liferay.portal.security.permission.ResourceActionsUtil;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ResourceBlockLocalServiceUtil;
import com.liferay.portal.service.ResourceBlockPermissionLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.TeamLocalServiceUtil;
import com.thoughtworks.xstream.XStream;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/15/13
 * Time: 9:34 AM
 * To change this template use File | Settings | File Templates.
 */
public class PortalDataContextImpl implements PortalDataContext {

	public PortalDataContextImpl(long companyId){
		_zipWriter = ZipWriterFactoryUtil.getZipWriter();
		_zipReader = null;
		_xStream = new XStream();
		_companyId = companyId;
	}

	public PortalDataContextImpl(long companyId, ZipReader zipReader){
		_zipWriter = null;
		_zipReader = zipReader;
		_xStream = new XStream();
		_companyId = companyId;
	}

	public void addPermissions(String resourceName, long resourcePK)
		throws PortalException, SystemException {

		List<KeyValuePair> permissions = new ArrayList<KeyValuePair>();

		Group group = null;

		if(_groupId != 0){
			group = GroupLocalServiceUtil.getGroup(_groupId);
		}

		List<Role> roles = RoleLocalServiceUtil.getRoles(_companyId);

		PrimitiveLongList roleIds = new PrimitiveLongList(roles.size());
		Map<Long, String> roleIdsToNames = new HashMap<Long, String>();

		for (Role role : roles) {
			int type = role.getType();

			if ((type == RoleConstants.TYPE_REGULAR) ||
				((type == RoleConstants.TYPE_ORGANIZATION) &&
					group != null && group.isOrganization()) ||
				((type == RoleConstants.TYPE_SITE) && group != null &&
					(group.isLayoutSetPrototype() || group.isSite()))) {

				String name = role.getName();

				roleIds.add(role.getRoleId());
				roleIdsToNames.put(role.getRoleId(), name);
			}
			else if ((type == RoleConstants.TYPE_PROVIDER) && role.isTeam()) {
				Team team = TeamLocalServiceUtil.getTeam(role.getClassPK());

				if (team.getGroupId() == _groupId) {
					String name = ROLE_TEAM_PREFIX + team.getName();

					roleIds.add(role.getRoleId());
					roleIdsToNames.put(role.getRoleId(), name);
				}
			}
		}

		List<String> actionIds = ResourceActionsUtil.getModelResourceActions(
			resourceName);

		Map<Long, Set<String>> roleIdsToActionIds = getActionIds(
			_companyId, roleIds.getArray(), resourceName, resourcePK,
			actionIds);

		for (Map.Entry<Long, String> entry : roleIdsToNames.entrySet()) {
			long roleId = entry.getKey();
			String name = entry.getValue();

			Set<String> availableActionIds = roleIdsToActionIds.get(roleId);

			if (availableActionIds == null) {
				availableActionIds = Collections.emptySet();
			}

			KeyValuePair permission = new KeyValuePair(
				name, StringUtil.merge(availableActionIds));

			permissions.add(permission);
		}

		_permissionsMap.put(
			getPrimaryKeyString(resourceName, resourcePK), permissions);
	}

	public void addPermissions(String resourceName, long resourcePK,
		List<KeyValuePair> permissions) {

		_permissionsMap.put(
			getPrimaryKeyString(resourceName, resourcePK), permissions);
	}

	public void addZipEntry(String path, Object object){

		try{
			_zipWriter.addEntry(path, _xStream.toXML(object));
		}
		catch(IOException e){
			e.printStackTrace();
		}

	}

	public void addZipEntry(String path, String document){

		try{
			_zipWriter.addEntry(path, document);
		}
		catch(IOException e){
			e.printStackTrace();
		}

	}

	public Object fromXML(String xml) {
		if (Validator.isNull(xml)) {
			return null;
		}

		return _xStream.fromXML(xml);
	}

	public Map<Long, Set<String>> getActionIds(long companyId, long[] roleIds,
			String className, long primKey, List<String> actionIds)
		throws PortalException, SystemException {

		if (ResourceBlockLocalServiceUtil.isSupported(className)) {
			return ResourceBlockPermissionLocalServiceUtil.
				getAvailableResourceBlockPermissionActionIds(
					roleIds, className, primKey, actionIds);
		}
		else {
			return ResourcePermissionLocalServiceUtil.
				getAvailableResourcePermissionActionIds(
					companyId, className, ResourceConstants.SCOPE_INDIVIDUAL,
					String.valueOf(primKey), roleIds, actionIds);
		}
	}

   	public long getClassPK(ClassedModel classedModel) {
		if (classedModel instanceof ResourcedModel) {
			ResourcedModel resourcedModel = (ResourcedModel)classedModel;

			return resourcedModel.getResourcePrimKey();
		}
		else {
			return (Long)classedModel.getPrimaryKeyObj();
		}
	}

	public long getCompanyId(){
		return _companyId;
	}

	public long getGroupId() {
		return _groupId;
	}

	public Map<String, List<KeyValuePair>> getPermissions() {
		return _permissionsMap;
	}

	public Object getZipEntryAsObject(String path) {
		return fromXML(getZipEntryAsString(path));
	}

	public String getZipEntryAsString(String path) {
		return getZipReader().getEntryAsString(path);
	}

	public ZipWriter getZipWriter() {
		return _zipWriter;
	}

	public void setGroupId(long groupId) {
		_groupId = groupId;
	}

	public void importPermissions(Class<?> clazz, long classPK, long newClassPK)
		throws PortalException, SystemException {

		importPermissions(clazz.getName(), classPK, newClassPK);
	}

	public void importPermissions(String resourceName, long resourcePK,
			long newResourcePK)
		throws PortalException, SystemException {

		List<KeyValuePair> permissions = _permissionsMap.get(
			getPrimaryKeyString(resourceName, resourcePK));

		if (permissions == null) {
			return;
		}

		Map<Long, String[]> roleIdsToActionIds = new HashMap<Long, String[]>();

		for (KeyValuePair permission : permissions) {
			String roleName = permission.getKey();

			Role role;

			Team team = null;

			if (roleName.startsWith(ROLE_TEAM_PREFIX)) {
				roleName = roleName.substring(ROLE_TEAM_PREFIX.length());

				try {
					team = TeamLocalServiceUtil.getTeam(_groupId, roleName);
				}
				catch (NoSuchTeamException nste) {
					continue;
				}
			}

			try {
				if (team != null) {
					role = RoleLocalServiceUtil.getTeamRole(
						_companyId, team.getTeamId());
				}
				else {
					role = RoleLocalServiceUtil.getRole(_companyId, roleName);
				}
			}
			catch (NoSuchRoleException nsre) {
				continue;
			}

			String[] actionIds = StringUtil.split(permission.getValue());

			roleIdsToActionIds.put(role.getRoleId(), actionIds);
		}

		if (roleIdsToActionIds.isEmpty()) {
			return;
		}

		if (ResourceBlockLocalServiceUtil.isSupported(resourceName)) {
			ResourceBlockLocalServiceUtil.setIndividualScopePermissions(
				_companyId, _groupId, resourceName, newResourcePK,
				roleIdsToActionIds);
		}
		else {
			ResourcePermissionLocalServiceUtil.setResourcePermissions(
				_companyId, resourceName, ResourceConstants.SCOPE_INDIVIDUAL,
				String.valueOf(newResourcePK), roleIdsToActionIds);
		}
	}

	public void importPermissions(ClassedModel classedModel,
			ClassedModel newClassedModel, String namespace)
		throws PortalException, SystemException {

		if (!isResourceMain(classedModel)) {
			return;
		}

		Class<?> clazz = classedModel.getModelClass();
		long classPK = getClassPK(classedModel);

		long newClassPK = getClassPK(newClassedModel);

		Map<Long, Long> newPrimaryKeysMap =
			(Map<Long, Long>)getNewPrimaryKeysMap(clazz);

		newPrimaryKeysMap.put(classPK, newClassPK);

		importPermissions(clazz, classPK, newClassPK);

	}

	protected ZipReader getZipReader(){
		return _zipReader;
	}

	protected String getPrimaryKeyString(String className, long classPK) {
		return getPrimaryKeyString(className, String.valueOf(classPK));
	}

	protected String getPrimaryKeyString(String className, String primaryKey) {
		return className.concat(StringPool.POUND).concat(primaryKey);
	}

	protected boolean isResourceMain(ClassedModel classedModel) {
		if (classedModel instanceof ResourcedModel) {
			ResourcedModel resourcedModel = (ResourcedModel)classedModel;

			return resourcedModel.isResourceMain();
		}

		return true;
	}

	protected Map<?, ?> getNewPrimaryKeysMap(Class<?> clazz) {
		return getNewPrimaryKeysMap(clazz.getName());
	}

	protected Map<?, ?> getNewPrimaryKeysMap(String className) {
		Map<?, ?> map = _newPrimaryKeysMaps.get(className);

		if (map == null) {
			map = new HashMap<Object, Object>();

			_newPrimaryKeysMaps.put(className, map);
		}

		return map;
	}

	private static final String ROLE_TEAM_PREFIX = "ROLE_TEAM_,*";

	private Map<String, List<KeyValuePair>> _permissionsMap = new HashMap<String, List<KeyValuePair>>();
	private Map<String, Map<?, ?>> _newPrimaryKeysMaps = new HashMap<String, Map<?, ?>>();

	private ZipWriter _zipWriter;
	private ZipReader _zipReader;
	private XStream _xStream;

	private long _companyId;
	private long _groupId;

}
