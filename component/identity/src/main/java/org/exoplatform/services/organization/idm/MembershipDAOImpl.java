/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.services.organization.idm;

import org.exoplatform.commons.utils.ListenerStack;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipEventListener;
import org.exoplatform.services.organization.MembershipHandler;
import org.exoplatform.services.organization.MembershipType;
import org.exoplatform.services.organization.User;
import org.picketlink.idm.api.IdentitySession;
import org.picketlink.idm.api.Role;
import org.picketlink.idm.api.RoleType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.naming.InvalidNameException;

/**
 */
public class MembershipDAOImpl implements MembershipHandler
{

   private PicketLinkIDMService service_;

   private List listeners_;

   private PicketLinkIDMOrganizationServiceImpl orgService;

   public MembershipDAOImpl(PicketLinkIDMOrganizationServiceImpl orgService, PicketLinkIDMService service)
   {
      service_ = service;
      listeners_ = new ListenerStack(5);
      this.orgService = orgService;
   }

   public void addMembershipEventListener(MembershipEventListener listener)
   {
      listeners_.add(listener);
   }

   final public Membership createMembershipInstance()
   {
      return new MembershipImpl();
   }

   public void createMembership(Membership m, boolean broadcast) throws Exception
   {

      if (broadcast)
      {
         preSave(m, true);
      }

      saveMembership(m, false);

      if (broadcast)
      {
         postSave(m, true);
      }

   }

   public void linkMembership(User user, Group g, MembershipType mt, boolean broadcast) throws Exception
   {
      if (g == null)
      {
         throw new InvalidNameException("Can not create membership record for " + user.getUserName()
            + " because group is null");
      }

      if (mt == null)
      {
         throw new InvalidNameException("Can not create membership record for " + user.getUserName()
            + " because membership type is null");
      }

      String groupId =
            getIdentitySession().getPersistenceManager().
               createGroupKey(g.getGroupName(), orgService.getConfiguration().getGroupType(g.getParentId()));


      if (isCreateMembership(mt.getName()))
      {
         if (getIdentitySession().getRoleManager().getRoleType(mt.getName()) == null)
         {
            getIdentitySession().getRoleManager().createRoleType(mt.getName());
         }


         if (getIdentitySession().getRoleManager().hasRole(user.getUserName(), groupId, mt.getName()))
         {
            return;
         }
      }

      if (isAssociationMapped() && getAssociationMapping().equals(mt.getName()))
      {
         getIdentitySession().getRelationshipManager().associateUserByKeys(groupId, user.getUserName());
      }

      MembershipImpl membership = new MembershipImpl();
      membership.setMembershipType(mt.getName());
      membership.setUserName(user.getUserName());
      membership.setGroupId(g.getId());

      if (broadcast)
      {
         preSave(membership, true);
      }

      getIdentitySession().getRoleManager().createRole(mt.getName(), user.getUserName(), groupId);

      if (broadcast)
      {
         postSave(membership, true);
      }

   }

   public void saveMembership(Membership m, boolean broadcast) throws Exception
   {
      String groupId =
         getIdentitySession().getPersistenceManager().
            createGroupKey(getGroupNameFromId(m.getGroupId()), getGroupTypeFromId(m.getGroupId()));

      if (getIdentitySession().getRoleManager().hasRole(m.getUserName(), groupId, m.getMembershipType()))
      {
         return;
      }

      if (broadcast)
      {
         preSave(m, false);
      }

      if (isCreateMembership(m.getMembershipType()))
      {

         getIdentitySession().getRoleManager().createRole(m.getMembershipType(), m.getUserName(), groupId);
      }
      if (isAssociationMapped() && getAssociationMapping().equals(m.getMembershipType()))
      {
         getIdentitySession().getRelationshipManager().associateUserByKeys(groupId, m.getUserName());
      }

      if (broadcast)
      {
         postSave(m, false);
      }
   }

   public Membership removeMembership(String id, boolean broadcast) throws Exception
   {

      Membership m = new MembershipImpl(id);

      String groupId =
         getIdentitySession().getPersistenceManager().
            createGroupKey(getGroupNameFromId(m.getGroupId()), getGroupTypeFromId(m.getGroupId()));

      if (!getIdentitySession().getRoleManager().hasRole(m.getUserName(), groupId, m.getMembershipType()))
      {
         return m;
      }

      if (broadcast)
      {
         preDelete(m);
      }

      if (isCreateMembership(m.getMembershipType()))
      {

         getIdentitySession().getRoleManager().removeRole(m.getMembershipType(), m.getUserName(), groupId);
      }

      if (isAssociationMapped() && getAssociationMapping().equals(m.getMembershipType()) &&
          getIdentitySession().getRelationshipManager().isAssociatedByKeys(m.getGroupId(), m.getUserName()))
      {
         Set<String> keys = new HashSet<String>();
         keys.add(m.getUserName());
         getIdentitySession().getRelationshipManager().disassociateUsersByKeys(groupId, keys);
      }

      if (broadcast)
      {
         postDelete(m);
      }
      return m;
   }

   public Collection removeMembershipByUser(String userName, boolean broadcast) throws Exception
   {

      Collection<Role> roles = getIdentitySession().getRoleManager().findRoles(userName, null);

      HashSet<MembershipImpl> memberships = new HashSet<MembershipImpl>();

      for (Role role : roles)
      {
         MembershipImpl m = new MembershipImpl();
         Group g = ((GroupDAOImpl)orgService.getGroupHandler()).convertGroup(role.getGroup());
         m.setGroupId(g.getId());
         m.setUserName(role.getUser().getId());
         m.setMembershipType(role.getRoleType().getName());
         memberships.add(m);

         if (broadcast)
         {
            preDelete(m);
         }

         getIdentitySession().getRoleManager().removeRole(role);

         if (broadcast)
         {
            postDelete(m);
         }

      }

      if (isAssociationMapped())
      {

         Collection<org.picketlink.idm.api.Group> groups =
            getIdentitySession().getRelationshipManager().findAssociatedGroups(userName, null);

         Set<String> keys = new HashSet<String>();
         keys.add(userName);
         
         for (org.picketlink.idm.api.Group group : groups)
         {
            getIdentitySession().getRelationshipManager().disassociateUsersByKeys(group.getKey(), keys);
         }

      }

      //TODO: Exo UI has hardcoded casts to List
      return new LinkedList(memberships);

   }

   public Membership findMembershipByUserGroupAndType(String userName, String groupId, String type) throws Exception
   {
      String gid =
         getIdentitySession().getPersistenceManager().
            createGroupKey(getGroupNameFromId(groupId), getGroupTypeFromId(groupId));

      boolean hasMembership = false;

      if (isAssociationMapped() && getAssociationMapping().equals(type) &&
         getIdentitySession().getRelationshipManager().isAssociatedByKeys(gid, userName))
      {
         hasMembership = true;
      }


      Role role = getIdentitySession().getRoleManager().getRole(type, userName, gid);

      if (role != null &&
          (!isAssociationMapped() ||
           !getAssociationMapping().equals(role.getRoleType()) ||
           !ignoreMappedMembershipType())
         )
      {
         hasMembership = true;
      }

      if (hasMembership)
      {

      
         MembershipImpl m = new MembershipImpl();
         m.setGroupId(groupId);
         m.setUserName(userName);
         m.setMembershipType(type);

         return m;
      }
      return null;
   }

   public Collection findMembershipsByUserAndGroup(String userName, String groupId) throws Exception
   {
      if (userName == null)
      {
         // julien fix : if user name is null, need to check if we do need to return a special group
         return Collections.emptyList();
      }

      String gid =
         getIdentitySession().getPersistenceManager().
            createGroupKey(getGroupNameFromId(groupId), getGroupTypeFromId(groupId));

      Collection<RoleType> roleTypes = getIdentitySession().getRoleManager().findRoleTypes(userName, gid, null);

      HashSet<MembershipImpl> memberships = new HashSet<MembershipImpl>();

      for (RoleType roleType : roleTypes)
      {
         if (isCreateMembership(roleType.getName()))
         {
            MembershipImpl m = new MembershipImpl();
            m.setGroupId(groupId);
            m.setUserName(userName);
            m.setMembershipType(roleType.getName());
            memberships.add(m);
         }   
      }

      if (isAssociationMapped() &&
          getIdentitySession().getRelationshipManager().isAssociatedByKeys(gid, userName))
      {
         MembershipImpl m = new MembershipImpl();
         m.setGroupId(groupId);
         m.setUserName(userName);
         m.setMembershipType(getAssociationMapping());
         memberships.add(m);
      }

      //TODO: Exo UI has hardcoded casts to List
      return new LinkedList(memberships);
   }

   public Collection findMembershipsByUser(String userName) throws Exception
   {
      Collection<Role> roles = getIdentitySession().getRoleManager().findRoles(userName, null);

      HashSet<MembershipImpl> memberships = new HashSet<MembershipImpl>();

      for (Role role : roles)
      {
         if (isCreateMembership(role.getRoleType().getName()))
         {
            MembershipImpl m = new MembershipImpl();
            Group g = ((GroupDAOImpl)orgService.getGroupHandler()).convertGroup(role.getGroup());
            m.setGroupId(g.getId());
            m.setUserName(role.getUser().getId());
            m.setMembershipType(role.getRoleType().getName());
            memberships.add(m);
         }
      }
      
      if (isAssociationMapped())
      {

         Collection<org.picketlink.idm.api.Group> groups =
            getIdentitySession().getRelationshipManager().findAssociatedGroups(userName, null);

         for (org.picketlink.idm.api.Group group : groups)
         {
            MembershipImpl m = new MembershipImpl();
            Group g = ((GroupDAOImpl)orgService.getGroupHandler()).convertGroup(group);
            m.setGroupId(g.getId());
            m.setUserName(userName);
            m.setMembershipType(getAssociationMapping());
            memberships.add(m);
         }
                 
      }


      return new LinkedList(memberships);
   }

//   static void removeMembershipEntriesOfGroup(PicketLinkIDMOrganizationServiceImpl orgService, Group group,
//      IdentitySession session) throws Exception
//   {
//      String gid = session.getPersistenceManager().
//         createGroupKey(group.getGroupName(), orgService.getConfiguration().getGroupType(group.getParentId()));
//
//      Collection<Role> roles = session.getRoleManager().findRoles(gid, null);
//
//      for (Role role : roles)
//      {
//         session.getRoleManager().removeRole(role);
//      }
//
//
//   }

   public Collection findMembershipsByGroup(Group group) throws Exception
   {
      return findMembershipsByGroupId(group.getId());
   }

   public Collection findMembershipsByGroupId(String groupId) throws Exception
   {
      String gid =
         getIdentitySession().getPersistenceManager().createGroupKey(getGroupNameFromId(groupId),
            getGroupTypeFromId(groupId));

      Collection<Role> roles = getIdentitySession().getRoleManager().findRoles(gid, null);

      HashSet<MembershipImpl> memberships = new HashSet<MembershipImpl>();

      for (Role role : roles)
      {
         if (isCreateMembership(role.getRoleType().getName()))
         {
            MembershipImpl m = new MembershipImpl();
            Group g = ((GroupDAOImpl)orgService.getGroupHandler()).convertGroup(role.getGroup());
            m.setGroupId(g.getId());
            m.setUserName(role.getUser().getId());
            m.setMembershipType(role.getRoleType().getName());
            memberships.add(m);
         }
      }

      if (isAssociationMapped())
      {

         Collection<org.picketlink.idm.api.User> users =
            getIdentitySession().getRelationshipManager().findAssociatedUsers(gid, false, null);

         for (org.picketlink.idm.api.User user : users)
         {
            MembershipImpl m = new MembershipImpl();
            m.setGroupId(groupId);
            m.setUserName(user.getId());
            m.setMembershipType(getAssociationMapping());
            memberships.add(m);
         }

      }

      //TODO: Exo UI has harcoded casts to List
      return new LinkedList(memberships);

   }

   public Membership findMembership(String id) throws Exception
   {
      Membership m = new MembershipImpl(id);

      String groupId =
         getIdentitySession().getPersistenceManager().createGroupKey(getGroupNameFromId(m.getGroupId()),
            getGroupTypeFromId(m.getGroupId()));

      if (isCreateMembership(m.getMembershipType()) &&
          getIdentitySession().getRoleManager().hasRole(m.getUserName(), groupId, m.getMembershipType()))
      {
         return m;
      }

      if (isAssociationMapped() && getAssociationMapping().equals(m.getMembershipType()) &&
          getIdentitySession().getRelationshipManager().isAssociatedByKeys(groupId, m.getUserName()))
      {
         return m;
      }




      return null;
   }

   private void preSave(Membership membership, boolean isNew) throws Exception
   {
      for (int i = 0; i < listeners_.size(); i++)
      {
         MembershipEventListener listener = (MembershipEventListener)listeners_.get(i);
         listener.preSave(membership, isNew);
      }
   }

   private void postSave(Membership membership, boolean isNew) throws Exception
   {
      for (int i = 0; i < listeners_.size(); i++)
      {
         MembershipEventListener listener = (MembershipEventListener)listeners_.get(i);
         listener.postSave(membership, isNew);
      }
   }

   private void preDelete(Membership membership) throws Exception
   {
      for (int i = 0; i < listeners_.size(); i++)
      {
         MembershipEventListener listener = (MembershipEventListener)listeners_.get(i);
         listener.preDelete(membership);
      }
   }

   private void postDelete(Membership membership) throws Exception
   {
      for (int i = 0; i < listeners_.size(); i++)
      {
         MembershipEventListener listener = (MembershipEventListener)listeners_.get(i);
         listener.postDelete(membership);
      }
   }

   private IdentitySession getIdentitySession() throws Exception
   {
      return service_.getIdentitySession();
   }

   private String getGroupNameFromId(String groupId)
   {
      String[] ids = groupId.split("/");

      return ids[ids.length - 1];
   }

   private String getGroupTypeFromId(String groupId)
   {

      String parentId = groupId.substring(0, groupId.lastIndexOf("/"));

      return orgService.getConfiguration().getGroupType(parentId);
   }

   protected boolean isAssociationMapped()
   {
      String mapping = orgService.getConfiguration().getAssociationMembershipType();

      if (mapping != null && mapping.length() > 0)
      {
         return true;
      }
      return false;
   }

   protected String getAssociationMapping()
   {
      return orgService.getConfiguration().getAssociationMembershipType();
   }

   protected boolean ignoreMappedMembershipType()
   {
      return orgService.getConfiguration().isIgnoreMappedMembershipType();
   }

   protected boolean isCreateMembership(String typeName)
   {
      if (isAssociationMapped() &&
          getAssociationMapping().equals(typeName) &&
          ignoreMappedMembershipType())
      {
         return false;
      }
      return true;
   }
}
