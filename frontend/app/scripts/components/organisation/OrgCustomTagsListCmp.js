(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('orgCustomTagsList', {
            controller: function($scope, PaginatedQuerySrv, FilteringSrv, TagSrv, UserSrv, NotificationSrv) {
                var self = this;

                self.tags = [];
                self.getUserInfo = UserSrv.getCache;

                this.$onInit = function() {
                    // TODO: FIXME
                    self.filtering = new FilteringSrv('tag', 'custom-tags.list', {
                        version: 'v1',
                        defaults: {
                            showFilters: true,
                            showStats: false,
                            pageSize: 15,
                            sort: ['+predicate']
                        },
                        defaultFilter: []
                    });

                    self.filtering.initContext(self.organisation.name)
                        .then(function() {
                            self.load();

                            $scope.$watch('$vm.list.pageSize', function (newValue) {
                                self.filtering.setPageSize(newValue);
                            });
                        });
                };

                this.load = function() {

                    self.list = new PaginatedQuerySrv({
                        name: 'organisation-custom-tags',
                        version: 'v1',
                        skipStream: true,
                        sort: self.filtering.context.sort,
                        loadAll: false,
                        pageSize: self.filtering.context.pageSize,
                        filter: this.filtering.buildQuery(),
                        operations: [
                            {
                                '_name': 'listTag'
                            },
                            {
                                '_name': 'freetags'
                            }
                        ],
                        onFailure: function(err) {
                            if(err && err.status === 400) {
                                self.filtering.resetContext();
                                self.load();
                            }
                        }
                    });
                };

                self.updateColour = function(id, colour) {
                    TagSrv.updateTag(id, {colour: colour})
                        .then(function(/*response*/) {
                            NotificationSrv.success('Tag list', 'Tag colour updated successfully');
                        })
                        .catch(function(err) {
                            NotificationSrv.error('Tag list', err.data, err.status);
                        })
                }

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

                this.filterBy = function(field, value) {
                    self.filtering.clearFilters()
                        .then(function(){
                            self.addFilterValue(field, value);
                        });
                };

                this.sortBy = function(sort) {
                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };

                this.sortByField = function(field) {
                    var context = this.filtering.context;
                    var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                    var sort = null;

                    if(currentSort.substr(1) !== field) {
                        sort = ['+' + field];
                    } else {
                        sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
                    }

                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };


                // this.showTemplate = function(template) {

                //     var promise = template._id ? CaseTemplateSrv.get(template._id) : $q.resolve(template);

                //     promise
                //         .then(function(response) {
                //             var modalInstance = $uibModal.open({
                //                 animation: true,
                //                 keyboard: false,
                //                 backdrop: 'static',
                //                 templateUrl: 'views/components/org/case-template/details.modal.html',
                //                 controller: 'OrgCaseTemplateModalCtrl',
                //                 controllerAs: '$vm',
                //                 size: 'max',
                //                 resolve: {
                //                     template: function() {
                //                         return response;
                //                     },
                //                     fields: function() {
                //                         return self.fields;
                //                     }
                //                 }
                //             });

                //             return modalInstance.result;
                //         })
                //         .then(function() {
                //             self.load();
                //         })
                //         .catch(function(err) {
                //             if(err && !_.isString(err)) {
                //                 NotificationSrv.error('Case Template Admin', err.data, err.status);
                //             }
                //         })
                // }

                // self.createTemplate = function(template) {
                //     return CaseTemplateSrv.create(template).then(
                //         function(/*response*/) {
                //             self.load();

                //             $scope.$emit('templates:refresh');

                //             NotificationSrv.log('The template [' + template.name + '] has been successfully created', 'success');
                //         },
                //         function(response) {
                //             NotificationSrv.error('TemplateCtrl', response.data, response.status);
                //         }
                //     );
                // };

                // self.importTemplate = function() {
                //     var modalInstance = $uibModal.open({
                //         animation: true,
                //         templateUrl: 'views/components/org/case-template/import.html',
                //         controller: 'AdminCaseTemplateImportCtrl',
                //         controllerAs: 'vm',
                //         size: 'lg'
                //     });

                //     modalInstance.result
                //         .then(function(template) {
                //             return self.createTemplate(template);
                //         })
                //         .catch(function(err) {
                //             if (err && err.status) {
                //                 NotificationSrv.error('TemplateCtrl', err.data, err.status);
                //             }
                //         });
                // };

                // self.deleteTemplate = function (template) {
                //     ModalUtilsSrv.confirm('Remove case template', 'Are you sure you want to delete this case template?', {
                //         okText: 'Yes, remove it',
                //         flavor: 'danger'
                //     })
                //         .then(function () {
                //             return CaseTemplateSrv.delete(template._id);
                //         })
                //         .then(function () {
                //             self.load();

                //             $scope.$emit('templates:refresh');
                //         });
                // };

                // self.exportTemplate = function (template) {
                //     CaseTemplateSrv.get(template._id)
                //         .then(function(response) {
                //             var fileName = 'Case-Template__' + response.name.replace(/\s/gi, '_') + '.json';

                //             // Create a blob of the data
                //             var fileToSave = new Blob([angular.toJson(_.omit(response, 'id'))], {
                //                 type: 'application/json',
                //                 name: fileName
                //             });

                //             // Save the file
                //             saveAs(fileToSave, fileName);
                //         })

                // };
            },
            controllerAs: '$vm',
            templateUrl: 'views/components/org/custom-tags/tag-list.html',
            bindings: {
                organisation: '<',
                templates: '=',
                fields: '<',
                onReload: '&',
                onEdit: '&'
            }
        });
})();
