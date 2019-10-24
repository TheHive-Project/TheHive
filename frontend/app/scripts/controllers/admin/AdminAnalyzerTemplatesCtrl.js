(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AdminAnalyzerTemplatesCtrl', AdminAnalyzerTemplatesCtrl)
        .controller('AdminAnalyzerTemplateDialogCtrl', AdminAnalyzerTemplateDialogCtrl)
        .controller('AdminAnalyzerTemplateImportCtrl', AdminAnalyzerTemplateImportCtrl)
        .controller('AdminAnalyzerTemplateDeleteCtrl', AdminAnalyzerTemplateDeleteCtrl);


    function AdminAnalyzerTemplatesCtrl($q, $uibModal, AnalyzerSrv, AnalyzerTemplateSrv, NotificationSrv) {
        var self = this;

        this.templates = [];
        this.analyzers = [];
        this.analyzerIds = [];
        this.analyzerCount = 0;


        this.load = function() {
            $q.all([
                AnalyzerTemplateSrv.list(),
                AnalyzerSrv.query()
            ]).then(function (response) {
                self.templates = response[0].data;
                self.analyzers = response[1];

                var cleared = _.mapObject(self.analyzers, function(val) {
                    delete val.template;

                    return val;
                });

                self.analyzers = cleared;

                return $q.resolve(self.analyzers);
            }, function(rejection) {
                NotificationSrv.error('Analyzer Templates', rejection.data, rejection.status);
            }).then(function (analyzersMap) {
                if(_.isEmpty(analyzersMap)) {
                    _.each(_.pluck(self.templates, 'analyzerId'), function(item) {
                        analyzersMap[item] = {
                            id: item
                        };
                    });
                }

                _.each(self.templates, function (tpl) {
                    if(analyzersMap[tpl.analyzerId]) {
                        analyzersMap[tpl.analyzerId].template = tpl;
                    }
                });

                self.analyzerIds = _.keys(analyzersMap);
                self.analyzerCount = self.analyzerIds.length;
            });
        };

        this.showTemplate = function (analyzer) {
            var modalInstance = $uibModal.open({
                //scope: $scope,
                templateUrl: 'views/partials/admin/analyzer-template-dialog.html',
                controller: 'AdminAnalyzerTemplateDialogCtrl',
                controllerAs: 'vm',
                size: 'max',
                resolve: {
                    analyzer: function () {
                        return angular.copy(analyzer);
                    }
                }
            });

            modalInstance.result
                .then(function() {
                    self.load();
                })
                .catch(function(err){
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Analyzer Templates', err.data, err.status);
                    }
                });
        };

        this.deleteTemplate = function(template) {
            var modalInstance = $uibModal.open({
                templateUrl: 'views/partials/admin/analyzer-template-delete.html',
                controller: 'AdminAnalyzerTemplateDeleteCtrl',
                controllerAs: 'vm',
                size: '',
                resolve: {
                    template: function() {
                        return template;
                    }
                }
            });

            modalInstance.result
                .then(function() {
                    self.load();
                })
                .catch(function(err){
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Analyzer Templates', err.data, err.status);
                    }
                });
        };

        this.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/analyzer-template-import.html',
                controller: 'AdminAnalyzerTemplateImportCtrl',
                controllerAs: 'vm',
                size: 'lg'
            });

            modalInstance.result
                .then(function() {
                    self.load();
                })
                .catch(function(err){
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Analyzer Templates', err.data, err.status);
                    }
                });
        };

        this.load();
    }

    function AdminAnalyzerTemplateDialogCtrl($uibModalInstance, AnalyzerTemplateSrv, NotificationSrv, analyzer) {
        this.analyzer = analyzer;
        this.editorOptions = {
            useWrapMode: true,
            showGutter: true
        };

        this.formData = _.pick(analyzer.template, 'id', 'content');
        this.formData.analyzerId = this.analyzer.name || this.analyzer.id;

        this.cancel = function () {
            $uibModalInstance.dismiss();
        };

        this.saveTemplate = function() {
            AnalyzerTemplateSrv.save(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminAnalyzerTemplateDialogCtrl', response.data, response.status);
                });
        };
    }

    function AdminAnalyzerTemplateDeleteCtrl($uibModalInstance, AnalyzerTemplateSrv, NotificationSrv, template) {
        this.template = template;

        this.ok = function () {
            AnalyzerTemplateSrv.delete(template.id)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminAnalyzerTemplateDeleteCtrl', response.data, response.status);
                });
        };
        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }

    function AdminAnalyzerTemplateImportCtrl($uibModalInstance, AnalyzerTemplateSrv, NotificationSrv) {
        this.formData = {};

        this.ok = function () {
            AnalyzerTemplateSrv.import(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminAnalyzerTemplateImportCtrl', response.data, response.status);
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
