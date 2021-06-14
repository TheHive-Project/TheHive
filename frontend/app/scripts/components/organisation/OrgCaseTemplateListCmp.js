(function () {
    'use strict';

    angular.module('theHiveComponents')
        .component('orgCaseTemplateList', {
            controller: function ($uibModal, $scope, $q, CaseTemplateSrv, PaginatedQuerySrv, FilteringSrv, UserSrv, NotificationSrv, ModalUtilsSrv) {
                var self = this;

                self.task = '';
                self.tags = [];
                self.templateCustomFields = [];
                self.templateIndex = -1;
                self.getUserInfo = UserSrv.getCache;


                this.$onInit = function () {
                    self.filtering = new FilteringSrv('caseTemplate', 'caseTemplate.list', {
                        version: 'v1',
                        defaults: {
                            showFilters: true,
                            showStats: false,
                            pageSize: 15,
                            sort: ['+displayName']
                        },
                        defaultFilter: []
                    });

                    self.filtering.initContext(self.organisation.name)
                        .then(function () {
                            self.load();

                            $scope.$watch('$vm.list.pageSize', function (newValue) {
                                self.filtering.setPageSize(newValue);
                            });
                        });
                };

                this.load = function () {

                    self.list = new PaginatedQuerySrv({
                        name: 'organisation-case-templates',
                        version: 'v1',
                        skipStream: true,
                        sort: self.filtering.context.sort,
                        loadAll: false,
                        pageSize: self.filtering.context.pageSize,
                        filter: this.filtering.buildQuery(),
                        operations: [{
                            '_name': 'getOrganisation',
                            'idOrName': self.organisation.name
                        },
                        {
                            '_name': 'caseTemplates'
                        }
                        ],
                        onFailure: function (err) {
                            if (err && err.status === 400) {
                                self.filtering.resetContext();
                                self.load();
                            }
                        }
                    });
                };

                // Filtering
                this.toggleFilters = function () {
                    this.filtering.toggleFilters();
                };

                this.filter = function () {
                    self.filtering.filter().then(this.applyFilters);
                };

                this.clearFilters = function () {
                    this.filtering.clearFilters()
                        .then(self.search);
                };

                this.removeFilter = function (index) {
                    self.filtering.removeFilter(index)
                        .then(self.search);
                };

                this.search = function () {
                    self.load();
                    self.filtering.storeContext();
                };
                this.addFilterValue = function (field, value) {
                    this.filtering.addFilterValue(field, value);
                    this.search();
                };

                this.filterBy = function (field, value) {
                    self.filtering.clearFilters()
                        .then(function () {
                            self.addFilterValue(field, value);
                        });
                };

                this.sortBy = function (sort) {
                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };

                this.sortByField = function (field) {
                    var context = this.filtering.context;
                    var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                    var sort = null;

                    if (currentSort.substr(1) !== field) {
                        sort = ['+' + field];
                    } else {
                        sort = [(currentSort === '+' + field) ? '-' + field : '+' + field];
                    }

                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };

                this.newTemplate = function () {
                    self.showTemplate({
                        name: '',
                        titlePrefix: '',
                        severity: 2,
                        tlp: 2,
                        pap: 2,
                        tags: [],
                        tasks: [],
                        customFields: {},
                        description: ''
                    });
                };

                this.showTemplate = function (template) {

                    var promise = template._id ? CaseTemplateSrv.get(template._id) : $q.resolve(template);

                    promise
                        .then(function (response) {
                            var modalInstance = $uibModal.open({
                                animation: true,
                                keyboard: false,
                                backdrop: 'static',
                                templateUrl: 'views/components/org/case-template/details.modal.html',
                                controller: 'OrgCaseTemplateModalCtrl',
                                controllerAs: '$vm',
                                size: 'max',
                                resolve: {
                                    template: function () {
                                        var tmpl = angular.copy(response);

                                        if (tmpl.tasks && tmpl.tasks.length > 0) {
                                            tmpl.tasks = _.sortBy(tmpl.tasks, 'order');
                                        }

                                        return tmpl;
                                    },
                                    fields: function () {
                                        return self.fields;
                                    }
                                }
                            });

                            return modalInstance.result;
                        })
                        .then(function () {
                            self.load();
                        })
                        .catch(function (err) {
                            if (err && !_.isString(err)) {
                                NotificationSrv.error('Case Template Admin', err.data, err.status);
                            }
                        })
                }

                self.createTemplate = function (template) {
                    return CaseTemplateSrv.create(template).then(
                        function (/*response*/) {
                            self.load();

                            $scope.$emit('templates:refresh');

                            NotificationSrv.log('The template [' + template.name + '] has been successfully created', 'success');
                        },
                        function (response) {
                            NotificationSrv.error('TemplateCtrl', response.data, response.status);
                        }
                    );
                };

                self.importTemplate = function () {
                    var modalInstance = $uibModal.open({
                        animation: true,
                        templateUrl: 'views/components/org/case-template/import.html',
                        controller: 'AdminCaseTemplateImportCtrl',
                        controllerAs: 'vm',
                        size: 'lg'
                    });

                    modalInstance.result
                        .then(function (template) {
                            return self.createTemplate(template);
                        })
                        .catch(function (err) {
                            if (err && err.status) {
                                NotificationSrv.error('TemplateCtrl', err.data, err.status);
                            }
                        });
                };

                self.deleteTemplate = function (template) {
                    ModalUtilsSrv.confirm('Remove case template', 'Are you sure you want to delete this case template?', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    })
                        .then(function () {
                            return CaseTemplateSrv.delete(template._id);
                        })
                        .then(function () {
                            self.load();

                            $scope.$emit('templates:refresh');
                        });
                };

                self.exportTemplate = function (template) {
                    CaseTemplateSrv.get(template._id)
                        .then(function (response) {
                            var fileName = 'Case-Template__' + response.name.replace(/\s/gi, '_') + '.json';

                            // Create a blob of the data
                            var fileToSave = new Blob([angular.toJson(_.omit(response, 'id'))], {
                                type: 'application/json',
                                name: fileName
                            });

                            // Save the file
                            saveAs(fileToSave, fileName);
                        })

                };
            },
            controllerAs: '$vm',
            templateUrl: 'views/components/org/case-template/case-templates.html',
            bindings: {
                organisation: '<',
                templates: '=',
                fields: '<',
                onReload: '&',
                onEdit: '&'
            }
        });
})();
