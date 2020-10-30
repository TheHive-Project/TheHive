(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AdminReportTemplatesCtrl', AdminReportTemplatesCtrl)
        .controller('AdminReportTemplateDialogCtrl', AdminReportTemplateDialogCtrl)
        .controller('AdminReportTemplateImportCtrl', AdminReportTemplateImportCtrl);


    function AdminReportTemplatesCtrl($q, $uibModal, ModalUtilsSrv, AnalyzerSrv, ReportTemplateSrv, NotificationSrv) {
        var self = this;

        this.templates = [];
        this.analyzers = [];
        this.analyzerIds = [];
        this.analyzerCount = 0;


        this.load = function() {
            $q.all([
                ReportTemplateSrv.list(),
                AnalyzerSrv.query()
            ]).then(function (response) {
                self.templates = response[0].data;
                self.analyzers = response[1];

                var cleared = _.mapObject(self.analyzers, function(val) {
                    delete val.shortReport;
                    delete val.longReport;

                    return val;
                });

                self.analyzers = cleared;

                return $q.resolve(self.analyzers);
            }, function(rejection) {
                NotificationSrv.error('ReportTemplates', rejection.data, rejection.status);
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
                        analyzersMap[tpl.analyzerId][tpl.reportType + 'Report'] = tpl;
                    }
                });

                self.analyzerIds = _.keys(analyzersMap);
                self.analyzerCount = self.analyzerIds.length;
            });
        };

        this.showTemplate = function (reportTemplate, analyzer) {
            var modalInstance = $uibModal.open({
                //scope: $scope,
                templateUrl: 'views/partials/admin/report-template-dialog.html',
                controller: 'AdminReportTemplateDialogCtrl',
                controllerAs: 'vm',
                size: 'max',
                resolve: {
                    reportTemplate: function () {
                        return reportTemplate;
                    },
                    analyzer: function () {
                        return analyzer;
                    }
                }
            });

            modalInstance.result.then(function() {
                NotificationSrv.log('Report template has been saved successfully', 'success');

                self.load();
            }).catch(function(err) {
                if(err && !_.isString(err)) {
                    NotificationSrv.error('AdminReportTemplateDialogCtrl', err.data, err.status);
                }
            });
        };

        this.deleteTemplate = function(template) {

            ModalUtilsSrv.confirm('Remove report template', 'Are you sure you want to delete the ' + template.reportType + ' report template for analyzer ' + template.analyzerId + '?', {
                okText: 'Yes, remove it',
                flavor: 'danger'
            }).then(function() {
                ReportTemplateSrv.delete(template.id)
                .then(function() {
                    self.load();
                    NotificationSrv.log('Report template has been permanently deleted', 'success');
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('AdminReportTemplateDialogCtrl', err.data, err.status);
                    }
                });
            });
        };

        this.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/report-template-import.html',
                controller: 'AdminReportTemplateImportCtrl',
                controllerAs: 'vm',
                size: 'lg'
            });

            modalInstance.result.then(function() {
                NotificationSrv.log('Report templates have been imported successfully', 'success');

                self.load();
            }).catch(function(err) {
                if(err && !_.isString(err)) {
                    NotificationSrv.error('AdminReportTemplateDialogCtrl', err.data, err.status);
                }
            });
        };

        this.load();
    }

    function AdminReportTemplateDialogCtrl($uibModalInstance, reportTemplate, ReportTemplateSrv, NotificationSrv, analyzer) {
        this.reportTemplate = reportTemplate;
        this.analyzer = analyzer;
        this.reportTypes = ['short', 'long'];
        this.editorOptions = {
            useWrapMode: true,
            showGutter: true
            //theme: 'chrome',
            //mode: 'xml'
        };

        this.formData = _.pick(reportTemplate, 'id', 'reportType', 'content');
        this.formData.analyzerId = this.analyzer.name || this.analyzer.id;

        this.cancel = function () {
            $uibModalInstance.dismiss();
        };

        this.saveTemplate = function() {
            ReportTemplateSrv.save(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminReportTemplateDialogCtrl', response.data, response.status);
                });
        };
    }

    function AdminReportTemplateImportCtrl($uibModalInstance, ReportTemplateSrv, NotificationSrv) {
        this.formData = {};

        this.ok = function () {
            ReportTemplateSrv.import(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminReportTemplateImportCtrl', response.data, response.status);
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
