(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCaseTemplatesCtrl',
        function($scope, $uibModal, TemplateSrv, NotificationSrv, UtilsSrv, ListSrv, MetricsCacheSrv, CustomFieldsCacheSrv) {
            $scope.task = '';
            $scope.tags = [];
            $scope.templates = [];
            $scope.metrics = [];
            $scope.fields = [];
            $scope.templateIndex = -1;

            $scope.sortableOptions = {
                handle: '.drag-handle',
                stop: function(/*e, ui*/) {
                    $scope.reorderTasks();
                },
                axis: 'y'
            };

            $scope.sortableFields = {
                handle: '.drag-handle',
                axis: 'y'
            };

            $scope.loadCache = function() {
                MetricsCacheSrv.all().then(function(metrics){
                    $scope.metrics = metrics;
                });

                CustomFieldsCacheSrv.all().then(function(fields){
                    $scope.fields = fields;
                });
            };
            $scope.loadCache();

            $scope.getList = function(index) {
                TemplateSrv.query(function(templates) {
                    $scope.templates = templates;
                    $scope.templateIndex = index;

                    if(templates.length > 0) {
                        $scope.loadTemplate(templates[index].id, $scope.templateIndex);
                    } else {
                        $scope.newTemplate();
                    }
                });
            };
            $scope.getList(0);

            $scope.loadTemplate = function(id, index) {
                TemplateSrv.get({
                    templateId: id
                }, function(template) {
                    delete template.createdAt;
                    delete template.createdBy;
                    delete template.updatedAt;
                    delete template.updatedBy;

                    $scope.template = template;
                    $scope.tags = UtilsSrv.objectify($scope.template.tags, 'text');
                });

                $scope.templateIndex = index;
            };

            $scope.newTemplate = function() {
                $scope.template = {
                    name: '',
                    titlePrefix: '',
                    severity: 2,
                    tlp: 2,
                    tags: [],
                    tasks: [],
                    metricNames: [],
                    customFieldNames: [],
                    description: ''
                };
                $scope.tags = [];
                $scope.templateIndex = -1;
            };

            $scope.reorderTasks = function() {
                _.each($scope.template.tasks, function(task, index) {
                    task.order = index;
                });
            };

            $scope.removeTask = function(task) {
                $scope.template.tasks = _.without($scope.template.tasks, task);
                $scope.reorderTasks();
            };

            $scope.addTask = function() {
                var order = $scope.template.tasks ? $scope.template.tasks.length : 0;

                $scope.openTaskDialog({order: order}, 'Add');
            };

            $scope.editTask = function(task) {
                $scope.openTaskDialog(task, 'Update');
            };

            $scope.openTaskDialog = function(task, action) {
                $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/admin/case-templates.task.html',
                    controller: 'AdminCaseTemplateTasksCtrl',
                    size: 'lg',
                    resolve: {
                        action: function() {
                            return action;
                        },
                        task: function() {
                            return task;
                        }
                    }
                });
            };

            $scope.addMetric = function(metric) {
                var metrics = $scope.template.metricNames || [];

                if(metrics.indexOf(metric.name) === -1) {
                    metrics.push(metric.name);
                    $scope.template.metricNames = metrics;
                } else {
                    NotificationSrv.log('The metric [' + metric.title + '] has already been added to the template', 'warning');
                }
            };

            $scope.removeMetric = function(metricName) {
                $scope.template.metricNames = _.without($scope.template.metricNames, metricName);
            };

            $scope.addCustomField = function(field) {
                var fields = $scope.template.customFieldNames || [];

                if(fields.indexOf(field.name) === -1) {
                    fields.push(field.name);
                    $scope.template.customFieldNames = fields;
                } else {
                    NotificationSrv.log('The custom field [' + field.label + '] has already been added to the template', 'warning');
                }
            };

            $scope.removeCustomField = function(fieldName) {
                $scope.template.customFieldNames = _.without($scope.template.customFieldNames, fieldName);
            };

            $scope.deleteTemplate = function() {
                $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/admin/case-templates.delete.html',
                    controller: 'AdminCaseTemplateDeleteCtrl',
                    size: ''
                });
            };

            $scope.saveTemplate = function() {
                $scope.template.tags = _.pluck($scope.tags, 'text');
                if (_.isEmpty($scope.template.id)) {
                    $scope.createTemplate();
                } else {
                    $scope.updateTemplate();
                }
            };

            $scope.createTemplate = function() {
                return TemplateSrv.save($scope.template, function() {
                    $scope.getList(0);

                    $scope.$emit('templates:refresh');

                    NotificationSrv.log('The template [' + $scope.template.name + '] has been successfully created', 'success');
                }, function(response) {
                    NotificationSrv.error('TemplateCtrl', response.data, response.status);
                });
            };

            $scope.updateTemplate = function() {
                return TemplateSrv.update({
                    templateId: $scope.template.id
                }, _.omit($scope.template, ['id', 'user', '_type']), function() {
                    $scope.getList($scope.templateIndex);

                    $scope.$emit('templates:refresh');

                    NotificationSrv.log('The template [' + $scope.template.name + '] has been successfully updated', 'success');
                }, function(response) {
                    NotificationSrv.error('TemplateCtrl', response.data, response.status);
                });
            };

        })
        .controller('AdminCaseTemplateTasksCtrl', function($scope, $uibModalInstance, action, task) {
            $scope.task = task || {};
            $scope.action = action;

            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };

            $scope.addTask = function() {
                if(action === 'Add') {
                    if($scope.template.tasks) {
                        $scope.template.tasks.push(task);
                    } else {
                        $scope.template.tasks = [task];
                    }                    
                }

                $uibModalInstance.dismiss();
            };
        })
        .controller('AdminCaseTemplateDeleteCtrl', function($scope, $uibModalInstance, TemplateSrv) {
            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };

            $scope.confirm = function() {
                TemplateSrv.delete({
                    templateId: $scope.template.id
                }, function() {
                    $scope.getList(0);

                    $scope.$emit('templates:refresh');

                    $uibModalInstance.dismiss();
                });
            };
        });
})();
