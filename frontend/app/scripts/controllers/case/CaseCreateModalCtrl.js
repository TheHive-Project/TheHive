(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCreateModalCtrl',
        function ($scope, $uibModalInstance, $uibModal, CaseSrv, SharingProfileSrv, AuthenticationSrv, TaxonomyCacheSrv, TagSrv, UserSrv, NotificationSrv, UtilsSrv, template, fields, organisation, sharingProfiles, userProfiles) {
            var self = this;

            this.template = template;
            this.fields = fields;
            self.task = '';
            self.tags = [];
            self.templateCustomFields = [];
            self.templateIndex = -1;
            self.currentUser = AuthenticationSrv.currentUser;
            self.sharingProfiles = sharingProfiles;
            self.userProfiles = userProfiles;
            self.sharingRules = SharingProfileSrv.SHARING_RULES;
            self.organisation = organisation;

            /**
             * Convert the template custom fields definition to a list of ordered field names
             * to be used for drag&drop sorting feature
             */
            var getTemplateCustomFields = function (customFields) {
                var result = [];

                result = _.sortBy(
                    _.map(customFields, function (definition, name) {
                        var fieldDef = self.fields[name];
                        var type = fieldDef ? fieldDef.type : null;

                        // The field doesn't exist, trying to find the field type from it's template value
                        if (type === null) {
                            var keys = _.without(_.keys(definition), 'order');
                            if (keys.length > 0) {
                                type = keys[0];
                            }
                        }

                        return {
                            name: name,
                            order: definition.order,
                            value: fieldDef ? definition[type] : null,
                            type: type
                        };
                    }),
                    'order'
                );

                return result;
            };

            this.$onInit = function () {
                self.fromTemplate = angular.isDefined(template) && !_.isEqual(self.template, {});

                self.sharingLinks = _.map(organisation.links, function (link) {
                    return _.extend({
                        organisation: link.toOrganisation,
                        linkType: link.linkType,
                    }, SharingProfileSrv.getCache(link.linkType), {
                        share: SharingProfileSrv.getCache(link.linkType).autoShare
                    });
                });
                self.autoSharingLinks = _.filter(self.sharingLinks, function (link) {
                    return link.autoShare;
                });

                if (self.fromTemplate === true) {

                    // Set basic info from template
                    self.newCase = _.defaults({
                        status: 'Open',
                        title: '',
                        description: self.template.description,
                        tlp: self.template.tlp,
                        pap: self.template.pap,
                        severity: self.template.severity
                    }, { tlp: 2, pap: 2 });

                    // Set tags from template
                    self.tags = UtilsSrv.objectify(self.template.tags, 'text');

                    // Set tasks from template
                    self.tasks = _.map(self.template.tasks, function (t) {
                        return t.title;
                    });

                } else {
                    self.tasks = [];
                    self.newCase = {
                        status: 'Open'
                    };
                }

                self.newCase.taskRule = self.organisation.taskRule;
                self.newCase.observableRule = self.organisation.observableRule;

                // self.tags = UtilsSrv.objectify(self.template.tags, 'text');
                self.templateCustomFields = getTemplateCustomFields(self.template.customFields);
            }

            this.cancel = function () {
                $uibModalInstance.dismiss();
            };

            self.dateOptions = {
                closeOnDateSelection: true,
                formatYear: 'yyyy',
                startingDay: 1
            };

            self.sortableOptions = {
                handle: '.drag-handle',
                stop: function ( /*e, ui*/) {
                    self.reorderTasks();
                },
                axis: 'y'
            };

            self.sortableFields = {
                handle: '.drag-handle',
                axis: 'y'
            };

            self.keys = function (obj) {
                if (!obj) {
                    return [];
                }
                return _.keys(obj);
            };

            self.fromTagLibrary = function () {
                TaxonomyCacheSrv.openTagLibrary()
                    .then(function (tags) {
                        self.tags = self.tags.concat(tags);
                    })
            };

            self.shareAsProfile = function (link, userProfile) {
                link.share = true;
                link.permissionProfile = userProfile;
            }

            self.openTaskDialog = function (task, action) {
                var modal = $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/components/org/case-template/case-templates.task.html',
                    controller: 'AdminCaseTemplateTasksCtrl',
                    size: 'lg',
                    resolve: {
                        action: function () {
                            return action;
                        },
                        task: function () {
                            return _.extend({}, task);
                        },
                        users: function () {
                            return UserSrv.list(
                                self.currentUser.organisation,
                                {
                                    filter: {
                                        _is: {
                                            _field: 'locked',
                                            _value: false
                                        }
                                    },
                                    sort: ['+name']
                                }
                            );
                        },
                        groups: function () {
                            var existingGroups = _.uniq(_.pluck(self.template.tasks, 'group').sort());

                            return existingGroups.length === 0 ? ['default'] : existingGroups;
                        }
                    }
                });

                modal.result.then(function (data) {
                    if (action === 'Add') {
                        if (self.template.tasks) {
                            self.template.tasks.push(data);
                        } else {
                            self.template.tasks = [data];
                        }
                    } else {
                        self.template.tasks[data.order] = data;
                    }
                });
            };

            self.addCustomFieldRow = function () {
                self.templateCustomFields.push({
                    name: null,
                    order: self.templateCustomFields.length + 1,
                    value: null
                });
            };

            self.removeCustomField = function (field) {
                self.templateCustomFields = _.without(self.templateCustomFields, field);
            };

            self.updateCustomField = function (field, value) {
                field.value = value;
            };

            self.getTags = function (query) {
                return TagSrv.autoComplete(query);
            };

        }
    );
})();
