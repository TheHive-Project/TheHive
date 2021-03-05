(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('OrgCaseTemplateModalCtrl', function ($scope, $uibModalInstance, $uibModal, CaseTemplateSrv, AuthenticationSrv, TaxonomyCacheSrv, TagSrv, UserSrv, NotificationSrv, UtilsSrv, template, fields) {
            var self = this;

            this.template = template;
            this.fields = fields;
            self.task = '';
            self.tags = [];
            self.templateCustomFields = [];
            self.templateIndex = -1;
            self.currentUser = AuthenticationSrv.currentUser;

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
                if (self.template._id) {
                    self.action = 'Edit';
                } else {
                    self.action = 'Add';
                }

                self.tags = UtilsSrv.objectify(self.template.tags, 'text');
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

            self.reorderTasks = function () {
                _.each(self.template.tasks, function (task, index) {
                    task.order = index;
                });
            };

            self.removeTask = function (task) {
                self.template.tasks = _.without(self.template.tasks, task);
                self.reorderTasks();
            };

            self.addTask = function () {
                var order = self.template.tasks ? self.template.tasks.length : 0;

                self.openTaskDialog({
                    order: order
                }, 'Add');
            };

            self.editTask = function (task) {
                self.openTaskDialog(task, 'Update');
            };

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

            self.saveTemplate = function () {
                // Set tags
                self.template.tags = _.pluck(self.tags, 'text');

                // Set custom fields
                self.template.customFields = {};
                _.each(self.templateCustomFields, function (cf, index) {
                    var fieldDef = self.fields[cf.name];
                    var value = null;
                    if (fieldDef) {
                        value = fieldDef.type === 'date' && cf.value ? moment(cf.value).valueOf() : cf.value;
                    }

                    self.template.customFields[cf.name] = {};
                    self.template.customFields[cf.name][fieldDef ? fieldDef.type : cf.type] = value;
                    self.template.customFields[cf.name].order = index + 1;
                });

                if (_.isEmpty(self.template.id)) {
                    self.createTemplate(self.template);
                } else {
                    self.updateTemplate(self.template);
                }
            };

            self.createTemplate = function (template) {
                return CaseTemplateSrv.create(template).then(
                    function (/*response*/) {
                        $scope.$emit('templates:refresh');

                        NotificationSrv.log('The template [' + template.name + '] has been successfully created', 'success');
                        $uibModalInstance.close();
                    },
                    function (response) {
                        NotificationSrv.error('TemplateCtrl', response.data, response.status);
                    }
                );
            };

            self.updateTemplate = function (template) {
                return CaseTemplateSrv.update(template.id, _.omit(template, 'id', 'user')).then(
                    function ( /*response*/) {
                        $scope.$emit('templates:refresh');

                        NotificationSrv.log('The template [' + template.name + '] has been successfully updated', 'success');
                        $uibModalInstance.close();
                    },
                    function (response) {
                        NotificationSrv.error('TemplateCtrl', response.data, response.status);
                    }
                );
            };

            self.getTags = function (query) {
                return TagSrv.autoComplete(query);
            };
        });
})();
