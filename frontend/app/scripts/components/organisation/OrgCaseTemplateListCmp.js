(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('orgCaseTemplateList', {
            controller: function($uibModal, $scope, CaseTemplateSrv, TagSrv, UserSrv, TaxonomyCacheSrv, AuthenticationSrv, NotificationSrv, UtilsSrv, ModalUtilsSrv) {
                var self = this;

                self.task = '';
                self.tags = [];
                self.templateCustomFields = [];
                self.templateIndex = -1;
                self.getUserInfo = UserSrv.getCache;
                self.currentUser = AuthenticationSrv.currentUser;

                /**
                 * Convert the template custom fields definition to a list of ordered field names
                 * to be used for drag&drop sorting feature
                 */
                var getTemplateCustomFields = function(customFields) {
                    var result = [];

                    result = _.sortBy(
                        _.map(customFields, function(definition, name) {
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

                self.dateOptions = {
                    closeOnDateSelection: true,
                    formatYear: 'yyyy',
                    startingDay: 1
                };

                self.sortableOptions = {
                    handle: '.drag-handle',
                    stop: function( /*e, ui*/ ) {
                        self.reorderTasks();
                    },
                    axis: 'y'
                };

                self.sortableFields = {
                    handle: '.drag-handle',
                    axis: 'y'
                };

                self.keys = function(obj) {
                    if (!obj) {
                        return [];
                    }
                    return _.keys(obj);
                };

                self.getList = function(id) {
                    CaseTemplateSrv.list().then(function(templates) {
                        self.templates = templates;

                        if (templates.length === 0) {
                            self.templateIndex = 0;
                            self.newTemplate();
                        } else if (id) {
                            self.loadTemplateById(id);
                        } else {
                            self.loadTemplateById(templates[0].id, 0);
                        }
                    });
                };

                self.loadTemplate = function(template, index) {
                    if (!template) {
                        return;
                    }

                    var filteredKeys = _.filter(_.keys(template), function(k) {
                        return k.startsWith('_');
                    }).concat(['createdAt', 'updatedAt', 'createdBy', 'updatedBy']);

                    self.template = _.defaults(_.omit(template, filteredKeys), {
                        pap: 2,
                        tlp: 2
                    });
                    self.template.tasks = _.sortBy(self.template.tasks, 'order');
                    self.tags = UtilsSrv.objectify(self.template.tags, 'text');
                    self.templateCustomFields = getTemplateCustomFields(template.customFields);

                    self.templateIndex = index || _.indexOf(self.templates, _.findWhere(self.templates, {
                        id: template.id
                    }));
                };

                self.$onInit = function() {
                    if (self.templates && !_.isEmpty(self.templates)) {
                        self.loadTemplate(self.templates[0]);
                    }
                };

                self.loadTemplateById = function(id) {
                    CaseTemplateSrv.get(id).then(function(template) {
                        self.loadTemplate(template);
                    });
                };

                self.fromTagLibrary = function() {
                    TaxonomyCacheSrv.openTagLibrary()
                        .then(function(tags){
                            self.tags = self.tags.concat(tags);
                        })
                };

                self.newTemplate = function() {
                    self.template = {
                        name: '',
                        titlePrefix: '',
                        severity: 2,
                        tlp: 2,
                        pap: 2,
                        tags: [],
                        tasks: [],
                        customFields: {},
                        description: ''
                    };
                    self.tags = [];
                    self.templateIndex = -1;
                    self.templateCustomFields = [];
                };

                self.reorderTasks = function() {
                    _.each(self.template.tasks, function(task, index) {
                        task.order = index;
                    });
                };

                self.removeTask = function(task) {
                    self.template.tasks = _.without(self.template.tasks, task);
                    self.reorderTasks();
                };

                self.addTask = function() {
                    var order = self.template.tasks ? self.template.tasks.length : 0;

                    self.openTaskDialog({
                        order: order
                    }, 'Add');
                };

                self.editTask = function(task) {
                    self.openTaskDialog(task, 'Update');
                };

                self.openTaskDialog = function(task, action) {
                    var modal = $uibModal.open({
                        scope: $scope,
                        templateUrl: 'views/components/org/case-template/case-templates.task.html',
                        controller: 'AdminCaseTemplateTasksCtrl',
                        size: 'lg',
                        resolve: {
                            action: function() {
                                return action;
                            },
                            task: function() {
                                return _.extend({}, task);
                            },
                            users: function() {
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
                            groups: function() {
                                var existingGroups = _.uniq(_.pluck(self.template.tasks, 'group').sort());

                                return existingGroups.length === 0 ? ['default'] : existingGroups;
                            }
                        }
                    });

                    modal.result.then(function(data) {
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

                self.addCustomFieldRow = function() {
                    self.templateCustomFields.push({
                        name: null,
                        order: self.templateCustomFields.length + 1,
                        value: null
                    });
                };

                self.removeCustomField = function(field) {
                    self.templateCustomFields = _.without(self.templateCustomFields, field);
                };

                self.updateCustomField = function(field, value) {
                    field.value = value;
                };

                self.deleteTemplate = function() {
                    ModalUtilsSrv.confirm('Remove case template', 'Are you sure you want to delete this case template?', {
                            okText: 'Yes, remove it',
                            flavor: 'danger'
                        })
                        .then(function() {
                            return CaseTemplateSrv.delete(self.template.id);
                        })
                        .then(function() {
                            self.getList();

                            $scope.$emit('templates:refresh');
                        });
                };

                self.saveTemplate = function() {
                    // Set tags
                    self.template.tags = _.pluck(self.tags, 'text');

                    // Set custom fields
                    self.template.customFields = {};
                    _.each(self.templateCustomFields, function(cf, index) {
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

                self.createTemplate = function(template) {
                    return CaseTemplateSrv.create(template).then(
                        function(response) {
                            self.getList(response.data.id);

                            $scope.$emit('templates:refresh');

                            NotificationSrv.log('The template [' + template.name + '] has been successfully created', 'success');
                        },
                        function(response) {
                            NotificationSrv.error('TemplateCtrl', response.data, response.status);
                        }
                    );
                };

                self.updateTemplate = function(template) {
                    return CaseTemplateSrv.update(template.id, _.omit(template, 'id', 'user')).then(
                        function( /*response*/ ) {
                            self.getList(template.id);

                            $scope.$emit('templates:refresh');

                            NotificationSrv.log('The template [' + template.name + '] has been successfully updated', 'success');
                        },
                        function(response) {
                            NotificationSrv.error('TemplateCtrl', response.data, response.status);
                        }
                    );
                };

                self.exportTemplate = function() {
                    var fileName = 'Case-Template__' + self.template.name.replace(/\s/gi, '_') + '.json';

                    // Create a blob of the data
                    var fileToSave = new Blob([angular.toJson(_.omit(self.template, 'id'))], {
                        type: 'application/json',
                        name: fileName
                    });

                    // Save the file
                    saveAs(fileToSave, fileName);
                };

                self.importTemplate = function() {
                    var modalInstance = $uibModal.open({
                        animation: true,
                        templateUrl: 'views/components/org/case-template/import.html',
                        controller: 'AdminCaseTemplateImportCtrl',
                        controllerAs: 'vm',
                        size: 'lg'
                    });

                    modalInstance.result
                        .then(function(template) {
                            return self.createTemplate(template);
                        })
                        .catch(function(err) {
                            if (err && err.status) {
                                NotificationSrv.error('TemplateCtrl', err.data, err.status);
                            }
                        });
                };

                self.getTags = function(query) {
                    return TagSrv.fromCases(query);
                };
            },
            controllerAs: '$vm',
            templateUrl: 'views/components/org/case-template/case-templates.html',
            bindings: {
                templates: '=',
                fields: '<',
                onReload: '&',
                onEdit: '&'
            }
        });
})();
